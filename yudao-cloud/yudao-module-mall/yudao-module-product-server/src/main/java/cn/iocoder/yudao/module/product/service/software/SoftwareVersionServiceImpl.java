package cn.iocoder.yudao.module.product.service.software;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareVersionDO;
import cn.iocoder.yudao.module.product.dal.mysql.software.SoftwareVersionMapper;
import cn.iocoder.yudao.module.product.enums.software.SoftwareArchEnum;
import cn.iocoder.yudao.module.product.enums.software.SoftwarePlatformEnum;
import cn.iocoder.yudao.module.product.enums.software.SoftwareVersionStatusEnum;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.product.enums.software.SoftwareLogRecordConstants.*;

/**
 * 软件版本 Service 实现类（013）
 */
@Service
@Validated
public class SoftwareVersionServiceImpl implements SoftwareVersionService {

    /** 语义化三段式（Assumptions） */
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
    /** Base64(sha512) 固定 88 位 */
    private static final int SHA512_BASE64_LENGTH = 88;

    @Resource
    private SoftwareVersionMapper versionMapper;
    @Resource
    private SoftwareService softwareService;
    @Resource
    private SoftwareStorageService storageService;
    @Resource
    private SoftwareFileChecker fileChecker;

    @Override
    public Long registerVersion(SoftwareVersionDO version) {
        validateRegister(version);
        // 落库为草稿；未确认上传前不可被任何入口消费（FR-337）
        version.setId(null);
        version.setStatus(SoftwareVersionStatusEnum.DRAFT.getStatus());
        version.setCurrentFlag(null);
        versionMapper.insert(version);
        return version.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = SOFTWARE_TYPE, subType = SOFTWARE_VERSION_PUBLISH_SUB_TYPE,
            bizNo = "{{#id}}", success = SOFTWARE_VERSION_PUBLISH_SUCCESS)
    public void publishVersion(Long id, boolean current) {
        SoftwareVersionDO version = validateVersionExists(id);
        if (SoftwareVersionStatusEnum.UNPUBLISHED.getStatus().equals(version.getStatus())) {
            // 已下架的版本必须重新登记，不能直接复活为当前发布
            throw exception(SOFTWARE_VERSION_NOT_PUBLISHED);
        }
        SoftwareVersionDO update = new SoftwareVersionDO();
        update.setId(id);
        update.setStatus(SoftwareVersionStatusEnum.PUBLISHED.getStatus());
        if (current) {
            // 先清后置，同一事务；并发由唯一索引 uk_current 兜底（R79 / SC-136）
            versionMapper.clearCurrentFlag(version.getSoftwareId(), version.getPlatform(), version.getArch());
            update.setCurrentFlag(1);
        }
        try {
            versionMapper.updateById(update);
        } catch (DuplicateKeyException e) {
            throw exception(SOFTWARE_VERSION_CONFLICT);
        }
    }

    @Override
    @LogRecord(type = SOFTWARE_TYPE, subType = SOFTWARE_VERSION_UNPUBLISH_SUB_TYPE,
            bizNo = "{{#id}}", success = SOFTWARE_VERSION_UNPUBLISH_SUCCESS)
    public void unpublishVersion(Long id) {
        validateVersionExists(id);
        // 显式置 NULL（updateById 会忽略 null 字段，见 mapper 注释）
        versionMapper.unpublishById(id, SoftwareVersionStatusEnum.UNPUBLISHED.getStatus());
    }

    @Override
    @LogRecord(type = SOFTWARE_TYPE, subType = SOFTWARE_VERSION_DELETE_SUB_TYPE,
            bizNo = "{{#id}}", success = SOFTWARE_VERSION_DELETE_SUCCESS)
    public void deleteVersion(Long id) {
        validateVersionExists(id);
        // 软删除：文件与已发出的链接仍然可用（Q87 / FR-353）
        versionMapper.deleteById(id);
    }

    @Override
    public SoftwareVersionDO getVersion(Long id) {
        return versionMapper.selectById(id);
    }

    @Override
    public PageResult<SoftwareVersionDO> getVersionPage(Long softwareId, PageParam pageParam) {
        return versionMapper.selectPageBySoftwareId(softwareId, pageParam);
    }

    @Override
    public SoftwareVersionDO getCurrentVersion(Long softwareId, String platform, String arch) {
        if (softwareId == null || StrUtil.isBlank(platform) || StrUtil.isBlank(arch)) {
            return null;
        }
        return versionMapper.selectCurrent(softwareId, platform, arch);
    }

    @Override
    public List<SoftwareVersionDO> getCurrentVersionList(Collection<Long> softwareIds) {
        if (softwareIds == null || softwareIds.isEmpty()) {
            return Collections.emptyList();
        }
        return versionMapper.selectListCurrent(softwareIds);
    }

    // ==================== 校验 ====================

    private void validateRegister(SoftwareVersionDO version) {
        if (version.getSoftwareId() == null || softwareService.getSoftware(version.getSoftwareId()) == null) {
            throw exception(SOFTWARE_NOT_EXISTS);
        }
        if (StrUtil.isBlank(version.getVersion()) || !VERSION_PATTERN.matcher(version.getVersion()).matches()) {
            throw exception(SOFTWARE_FILE_INVALID);
        }
        if (SoftwarePlatformEnum.parse(version.getPlatform()) == null
                || SoftwareArchEnum.parse(version.getArch()) == null) {
            throw exception(SOFTWARE_FILE_INVALID);
        }
        if (StrUtil.isBlank(version.getFileName())) {
            throw exception(SOFTWARE_FILE_INVALID);
        }
        if (version.getFileSize() == null || version.getFileSize() <= 0) {
            throw exception(SOFTWARE_FILE_INVALID);
        }
        if (StrUtil.isBlank(version.getSha512()) || version.getSha512().length() != SHA512_BASE64_LENGTH) {
            throw exception(SOFTWARE_FILE_INVALID);
        }
        if (StrUtil.isBlank(version.getFileUrl()) || !storageService.isAllowedUrl(version.getFileUrl())) {
            throw exception(SOFTWARE_FILE_INVALID);
        }
        // 版本号唯一（FR-333）
        if (versionMapper.selectBySkuKey(version.getSoftwareId(), version.getPlatform(),
                version.getArch(), version.getVersion()) != null) {
            throw exception(SOFTWARE_VERSION_DUPLICATE);
        }
        // 回源 HEAD：文件必须真实存在且大小一致（FR-337）
        long remoteSize;
        try {
            remoteSize = fileChecker.getContentLength(version.getFileUrl());
        } catch (Exception e) {
            throw exception(SOFTWARE_FILE_INVALID);
        }
        if (remoteSize != version.getFileSize()) {
            throw exception(SOFTWARE_FILE_INVALID);
        }
    }

    private SoftwareVersionDO validateVersionExists(Long id) {
        SoftwareVersionDO version = id == null ? null : versionMapper.selectById(id);
        if (version == null) {
            throw exception(SOFTWARE_VERSION_NOT_EXISTS);
        }
        return version;
    }

}
