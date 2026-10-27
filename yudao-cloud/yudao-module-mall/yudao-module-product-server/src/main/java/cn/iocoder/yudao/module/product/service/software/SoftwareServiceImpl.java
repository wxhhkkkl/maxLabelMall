package cn.iocoder.yudao.module.product.service.software;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareDO;
import cn.iocoder.yudao.module.product.dal.mysql.software.SoftwareMapper;
import cn.iocoder.yudao.module.product.enums.software.SoftwareTypeEnum;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SOFTWARE_CATEGORY_NOT_EXISTS;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SOFTWARE_NOT_EXISTS;
import static cn.iocoder.yudao.module.product.enums.software.SoftwareLogRecordConstants.*;

/**
 * 软件 / 驱动 Service 实现类（013）
 */
@Service
@Validated
public class SoftwareServiceImpl implements SoftwareService {

    @Resource
    private SoftwareMapper softwareMapper;
    @Resource
    private SoftwareCategoryService categoryService;

    @Override
    @LogRecord(type = SOFTWARE_TYPE, subType = SOFTWARE_CREATE_SUB_TYPE,
            bizNo = "{{#software.title}}", success = SOFTWARE_CREATE_SUCCESS)
    public Long createSoftware(SoftwareDO software) {
        software.setId(null);
        validateCategory(software.getCategoryId());
        if (software.getStatus() == null) {
            software.setStatus(0);
        }
        if (software.getShowInSupport() == null) {
            software.setShowInSupport(false);
        }
        softwareMapper.insert(software);
        return software.getId();
    }

    @Override
    public void updateSoftware(SoftwareDO software) {
        validateSoftwareExists(software.getId());
        validateCategory(software.getCategoryId());
        softwareMapper.updateById(software);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = SOFTWARE_TYPE, subType = SOFTWARE_DELETE_SUB_TYPE,
            bizNo = "{{#id}}", success = SOFTWARE_DELETE_SUCCESS)
    public void deleteSoftware(Long id) {
        validateSoftwareExists(id);
        // 软删除：记录消失，但版本记录与对象存储文件保留（Q87）
        softwareMapper.deleteById(id);
    }

    @Override
    public SoftwareDO getSoftware(Long id) {
        return softwareMapper.selectById(id);
    }

    @Override
    public PageResult<SoftwareDO> getSoftwarePage(Long categoryId, Integer type, Integer status,
                                                  String title, PageParam pageParam) {
        return softwareMapper.selectPage(categoryId, type, status, title, pageParam);
    }

    @Override
    public List<SoftwareDO> getSupportSoftwareList(Collection<Long> categoryIds) {
        if (CollUtil.isEmpty(categoryIds)) {
            return Collections.emptyList();
        }
        return softwareMapper.selectListForSupport(categoryIds);
    }

    @Override
    public SoftwareDO getEnabledDesktopSoftware(Long softwareId) {
        if (softwareId == null) {
            return null;
        }
        SoftwareDO software = softwareMapper.selectById(softwareId);
        if (software == null
                || !SoftwareTypeEnum.DESKTOP.getType().equals(software.getType())
                || !Integer.valueOf(0).equals(software.getStatus())) {
            return null;
        }
        return software;
    }

    @Override
    public SoftwareDO getDefaultDesktopSoftware() {
        List<SoftwareDO> list = softwareMapper.selectListByTypeAndStatus(
                SoftwareTypeEnum.DESKTOP.getType(), 0);
        return list.isEmpty() ? null : list.get(0);
    }

    private void validateSoftwareExists(Long id) {
        if (id == null || softwareMapper.selectById(id) == null) {
            throw exception(SOFTWARE_NOT_EXISTS);
        }
    }

    private void validateCategory(Long categoryId) {
        if (categoryId == null || categoryService.getCategory(categoryId) == null) {
            throw exception(SOFTWARE_CATEGORY_NOT_EXISTS);
        }
    }

}
