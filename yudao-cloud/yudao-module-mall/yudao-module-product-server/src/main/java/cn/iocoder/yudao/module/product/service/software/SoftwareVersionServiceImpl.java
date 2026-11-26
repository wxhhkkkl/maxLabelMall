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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Service
@Validated
public class SoftwareVersionServiceImpl implements SoftwareVersionService {

    /** 语义化三段式（Assumptions） */
    // 允许 1~4 段：驱动常见 4 段（如 7.4.2.3）；桌面端软件仍建议三段（electron-updater 用 semver）
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+(\\.\\d+)?$");
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

        // uk_version 不含 deleted：软删除的墓碑仍占着键位，而逻辑删除查询看不到它。
        // 「删了再传同一个版本号」要先把墓碑找出来 —— 还活着就是重复，已删除就复活它。
        SoftwareVersionDO existing = versionMapper.selectBySkuKeyIncludingDeleted(
                version.getSoftwareId(), version.getPlatform(), version.getArch(), version.getVersion());
        if (existing != null) {
            if (!Boolean.TRUE.equals(existing.getDeleted())) {
                throw exception(SOFTWARE_VERSION_DUPLICATE);
            }
            // 复活墓碑并覆盖成新文件；旧文件仍在对象存储，已发出的旧链接照常可下载（FR-353）
            version.setId(existing.getId());
            versionMapper.reviveDeletedVersion(version);
            return existing.getId();
        }

        try {
            versionMapper.insert(version);
        } catch (DuplicateKeyException e) {
            // 并发登记同一版本：唯一索引兜底，转成可读的业务错误
            throw exception(SOFTWARE_VERSION_DUPLICATE);
        }
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
        // 软删除：文件与已发出的链接仍然可用（Q87 / FR-353）；
        // 顺手清掉 current_flag —— 墓碑留着 1 会占住 uk_current，挡住同组合的下一个当前发布
        versionMapper.deleteByIdClearingCurrent(id);
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
            log.warn("[registerVersion] 版本号格式非法: {}", version.getVersion());
            throw exception(SOFTWARE_FILE_INVALID);
        }
        if (SoftwarePlatformEnum.parse(version.getPlatform()) == null
                || SoftwareArchEnum.parse(version.getArch()) == null) {
            log.warn("[registerVersion] 平台/架构非法: {}/{}", version.getPlatform(), version.getArch());
            throw exception(SOFTWARE_FILE_INVALID);
        }
        if (StrUtil.isBlank(version.getFileName())) {
            throw exception(SOFTWARE_FILE_INVALID);
        }
        if (version.getFileSize() == null || version.getFileSize() <= 0) {
            log.warn("[registerVersion] 文件大小非法: {}", version.getFileSize());
            throw exception(SOFTWARE_FILE_INVALID);
        }
        if (StrUtil.isBlank(version.getSha512()) || version.getSha512().length() != SHA512_BASE64_LENGTH) {
            log.warn("[registerVersion] sha512 长度非法: {}", StrUtil.length(version.getSha512()));
            throw exception(SOFTWARE_FILE_INVALID);
        }
        if (StrUtil.isBlank(version.getFileUrl()) || !storageService.isAllowedUrl(version.getFileUrl())) {
            log.warn("[registerVersion] 文件地址不在白名单: {}", version.getFileUrl());
            throw exception(SOFTWARE_FILE_INVALID);
        }
        // 版本号唯一（FR-333）由 registerVersion 里「含墓碑」的查重负责 ——
        // 这里不能查，逻辑删除会把墓碑过滤掉，反而放过后面撞索引的插入
        // 回源 HEAD：文件必须真实存在且大小一致（FR-337）
        long remoteSize;
        try {
            remoteSize = fileChecker.getContentLength(version.getFileUrl());
        } catch (Exception e) {
            log.warn("[registerVersion] 回源 HEAD 失败: {} — {}", version.getFileUrl(), e.getMessage());
            throw exception(SOFTWARE_FILE_INVALID);
        }
        if (remoteSize != version.getFileSize()) {
            log.warn("[registerVersion] 大小不一致: 登记 {} / 实际 {}", version.getFileSize(), remoteSize);
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
