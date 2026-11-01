package cn.iocoder.yudao.module.trade.service.customization;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationAreaRuleDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationMaterialDO;
import cn.iocoder.yudao.module.trade.dal.mysql.customization.CustomizationAreaRuleMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.customization.CustomizationMaterialMapper;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMIZATION_AREA_RULE_INVALID;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMIZATION_MATERIAL_NOT_AVAILABLE;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMIZATION_SPEC_OUT_OF_RANGE;
import static cn.iocoder.yudao.module.trade.enums.customization.CustomizationLogRecordConstants.CUSTOMIZATION_TYPE;
import static cn.iocoder.yudao.module.trade.enums.customization.CustomizationLogRecordConstants.MATERIAL_CREATE_SUB_TYPE;
import static cn.iocoder.yudao.module.trade.enums.customization.CustomizationLogRecordConstants.MATERIAL_CREATE_SUCCESS;
import static cn.iocoder.yudao.module.trade.enums.customization.CustomizationLogRecordConstants.MATERIAL_UPDATE_SUB_TYPE;
import static cn.iocoder.yudao.module.trade.enums.customization.CustomizationLogRecordConstants.MATERIAL_UPDATE_SUCCESS;

/**
 * 定制材质 Service 实现（014）
 */
@Service
@Validated
public class CustomizationMaterialServiceImpl implements CustomizationMaterialService {

    @Resource
    private CustomizationMaterialMapper customizationMaterialMapper;
    @Resource
    private CustomizationAreaRuleMapper customizationAreaRuleMapper;

    @Override
    public List<CustomizationMaterialDO> getEnabledMaterialList() {
        return customizationMaterialMapper.selectEnabledList();
    }

    @Override
    public CustomizationMaterialDO getMaterial(Long id) {
        return id == null ? null : customizationMaterialMapper.selectById(id);
    }

    @Override
    public CustomizationMaterialDO validateEnabledMaterial(Long id) {
        CustomizationMaterialDO material = getMaterial(id);
        if (material == null || !Integer.valueOf(0).equals(material.getStatus())) {
            throw exception(CUSTOMIZATION_MATERIAL_NOT_AVAILABLE);
        }
        return material;
    }

    @Override
    public List<CustomizationAreaRuleDO> getAreaRules(Long materialId) {
        return customizationAreaRuleMapper.selectListByMaterialId(materialId);
    }

    @Override
    public PageResult<CustomizationMaterialDO> getMaterialPage(String name, Integer status, PageParam pageParam) {
        return customizationMaterialMapper.selectPage(name, status, pageParam);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CUSTOMIZATION_TYPE, subType = MATERIAL_CREATE_SUB_TYPE,
            bizNo = "{{#material.name}}", success = MATERIAL_CREATE_SUCCESS)
    public Long createMaterial(CustomizationMaterialDO material, List<CustomizationAreaRuleDO> areaRules) {
        material.setId(null);
        validateMaterial(material);
        List<CustomizationAreaRuleDO> rules = validateAndSortRules(areaRules);
        if (material.getStatus() == null) {
            material.setStatus(0);
        }
        if (material.getSort() == null) {
            material.setSort(0);
        }
        customizationMaterialMapper.insert(material);
        insertRules(material.getId(), rules);
        return material.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @LogRecord(type = CUSTOMIZATION_TYPE, subType = MATERIAL_UPDATE_SUB_TYPE,
            bizNo = "{{#material.name}}", success = MATERIAL_UPDATE_SUCCESS)
    public void updateMaterial(CustomizationMaterialDO material, List<CustomizationAreaRuleDO> areaRules) {
        if (getMaterial(material.getId()) == null) {
            throw exception(CUSTOMIZATION_MATERIAL_NOT_AVAILABLE);
        }
        validateMaterial(material);
        List<CustomizationAreaRuleDO> rules = validateAndSortRules(areaRules);
        customizationMaterialMapper.updateById(material);
        // 区间整体替换：旧行逻辑删除后重新插入
        customizationAreaRuleMapper.deleteByMaterialId(material.getId());
        insertRules(material.getId(), rules);
    }

    @Override
    public void updateMaterialStatus(Long id, Integer status) {
        if (getMaterial(id) == null) {
            throw exception(CUSTOMIZATION_MATERIAL_NOT_AVAILABLE);
        }
        CustomizationMaterialDO update = new CustomizationMaterialDO();
        update.setId(id);
        update.setStatus(status);
        customizationMaterialMapper.updateById(update);
    }

    @Override
    public void deleteMaterial(Long id) {
        if (getMaterial(id) == null) {
            throw exception(CUSTOMIZATION_MATERIAL_NOT_AVAILABLE);
        }
        customizationMaterialMapper.deleteById(id);
    }

    // ==================== 校验 ====================

    private void validateMaterial(CustomizationMaterialDO material) {
        if (StrUtil.isBlank(material.getName()) || material.getBasePrice() == null || material.getBasePrice() <= 0) {
            throw exception(CUSTOMIZATION_MATERIAL_NOT_AVAILABLE);
        }
        if (material.getPrintCoefficient() == null || material.getPrintCoefficient().compareTo(BigDecimal.ZERO) <= 0
                || material.getPrintingCoefficient() == null
                || material.getPrintingCoefficient().compareTo(BigDecimal.ZERO) <= 0) {
            throw exception(CUSTOMIZATION_MATERIAL_NOT_AVAILABLE);
        }
        if (material.getMinQuantity() == null || material.getMaxQuantity() == null
                || material.getMinQuantity() < 1 || material.getMinQuantity() > material.getMaxQuantity()) {
            throw exception(CUSTOMIZATION_SPEC_OUT_OF_RANGE);
        }
        if (material.getMinWidth() == null || material.getMaxWidth() == null
                || material.getMinWidth() < 1 || material.getMinWidth() > material.getMaxWidth()
                || material.getMinHeight() == null || material.getMaxHeight() == null
                || material.getMinHeight() < 1 || material.getMinHeight() > material.getMaxHeight()) {
            throw exception(CUSTOMIZATION_SPEC_OUT_OF_RANGE);
        }
    }

    /**
     * 区间必须从 0 开始首尾相接、最后一条上界为 0（Q100=A）。
     */
    static List<CustomizationAreaRuleDO> validateAndSortRules(List<CustomizationAreaRuleDO> areaRules) {
        if (areaRules == null || areaRules.isEmpty()) {
            throw exception(CUSTOMIZATION_AREA_RULE_INVALID);
        }
        List<CustomizationAreaRuleDO> rules = new ArrayList<>(areaRules);
        rules.sort(Comparator.comparing(CustomizationAreaRuleDO::getMinArea));
        BigDecimal previousMax = null;
        for (int i = 0; i < rules.size(); i++) {
            CustomizationAreaRuleDO rule = rules.get(i);
            if (rule.getMinArea() == null || rule.getMaxArea() == null
                    || rule.getPrintCoefficient() == null
                    || rule.getPrintCoefficient().compareTo(BigDecimal.ZERO) <= 0
                    || rule.getDieCutCoefficient() == null
                    || rule.getDieCutCoefficient().compareTo(BigDecimal.ZERO) < 0) {
                throw exception(CUSTOMIZATION_AREA_RULE_INVALID);
            }
            if (i == 0 && rule.getMinArea().compareTo(BigDecimal.ZERO) != 0) {
                throw exception(CUSTOMIZATION_AREA_RULE_INVALID);
            }
            if (previousMax != null && rule.getMinArea().compareTo(previousMax) != 0) {
                throw exception(CUSTOMIZATION_AREA_RULE_INVALID);
            }
            boolean openEnded = rule.getMaxArea().compareTo(BigDecimal.ZERO) == 0;
            if (!openEnded && rule.getMaxArea().compareTo(rule.getMinArea()) <= 0) {
                throw exception(CUSTOMIZATION_AREA_RULE_INVALID);
            }
            if (openEnded && i != rules.size() - 1) {
                throw exception(CUSTOMIZATION_AREA_RULE_INVALID);
            }
            previousMax = rule.getMaxArea();
            rule.setSort(i);
        }
        if (rules.get(rules.size() - 1).getMaxArea().compareTo(BigDecimal.ZERO) != 0) {
            throw exception(CUSTOMIZATION_AREA_RULE_INVALID);
        }
        return rules;
    }

    private void insertRules(Long materialId, List<CustomizationAreaRuleDO> rules) {
        for (CustomizationAreaRuleDO rule : rules) {
            rule.setId(null);
            rule.setMaterialId(materialId);
            customizationAreaRuleMapper.insert(rule);
        }
    }

}
