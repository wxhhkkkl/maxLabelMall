package cn.iocoder.yudao.module.trade.service.customization;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationAreaRuleDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.customization.CustomizationMaterialDO;

import java.util.List;

/**
 * 定制材质 Service（014）
 */
public interface CustomizationMaterialService {

    /** 启用中的材质（C 端可选列表） */
    List<CustomizationMaterialDO> getEnabledMaterialList();

    /** 取材质；不存在返回 null */
    CustomizationMaterialDO getMaterial(Long id);

    /** 取材质并校验「存在且启用」，否则抛业务异常 */
    CustomizationMaterialDO validateEnabledMaterial(Long id);

    /** 某个材质的面积区间（按 minArea 升序） */
    List<CustomizationAreaRuleDO> getAreaRules(Long materialId);

    /** 管理端分页 */
    PageResult<CustomizationMaterialDO> getMaterialPage(String name, Integer status, PageParam pageParam);

    /** 新增（区间随材质同事务保存） */
    Long createMaterial(CustomizationMaterialDO material, List<CustomizationAreaRuleDO> areaRules);

    /** 修改（区间整体替换） */
    void updateMaterial(CustomizationMaterialDO material, List<CustomizationAreaRuleDO> areaRules);

    /** 启用 / 停用 */
    void updateMaterialStatus(Long id, Integer status);

    /** 软删除（历史订单用快照，不受影响） */
    void deleteMaterial(Long id);

}
