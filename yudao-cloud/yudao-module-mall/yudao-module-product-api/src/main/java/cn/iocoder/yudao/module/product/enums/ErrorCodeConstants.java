package cn.iocoder.yudao.module.product.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * Product 错误码枚举类
 *
 * product 系统，使用 1-008-000-000 段
 */
public interface ErrorCodeConstants {

    // ========== 商品分类相关 1-008-001-000 ============
    ErrorCode CATEGORY_NOT_EXISTS = new ErrorCode(1_008_001_000, "商品分类不存在");
    ErrorCode CATEGORY_PARENT_NOT_EXISTS = new ErrorCode(1_008_001_001, "父分类不存在");
    ErrorCode CATEGORY_PARENT_NOT_FIRST_LEVEL = new ErrorCode(1_008_001_002, "父分类不能是二级分类");
    ErrorCode CATEGORY_EXISTS_CHILDREN = new ErrorCode(1_008_001_003, "存在子分类，无法删除");
    ErrorCode CATEGORY_DISABLED = new ErrorCode(1_008_001_004, "商品分类({})已禁用，无法使用");
    ErrorCode CATEGORY_HAVE_BIND_SPU = new ErrorCode(1_008_001_005, "类别下存在商品，无法删除");

    // ========== 商品品牌相关编号 1-008-002-000 ==========
    ErrorCode BRAND_NOT_EXISTS = new ErrorCode(1_008_002_000, "品牌不存在");
    ErrorCode BRAND_DISABLED = new ErrorCode(1_008_002_001, "品牌已禁用");
    ErrorCode BRAND_NAME_EXISTS = new ErrorCode(1_008_002_002, "品牌名称已存在");

    // ========== 商品属性项 1-008-003-000 ==========
    ErrorCode PROPERTY_NOT_EXISTS = new ErrorCode(1_008_003_000, "属性项不存在");
    ErrorCode PROPERTY_EXISTS = new ErrorCode(1_008_003_001, "属性项的名称已存在");
    ErrorCode PROPERTY_DELETE_FAIL_VALUE_EXISTS = new ErrorCode(1_008_003_002, "属性项下存在属性值，无法删除");

    // ========== 商品属性值 1-008-004-000 ==========
    ErrorCode PROPERTY_VALUE_NOT_EXISTS = new ErrorCode(1_008_004_000, "属性值不存在");
    ErrorCode PROPERTY_VALUE_EXISTS = new ErrorCode(1_008_004_001, "属性值的名称已存在");

    // ========== 商品 SPU 1-008-005-000 ==========
    ErrorCode SPU_NOT_EXISTS = new ErrorCode(1_008_005_000, "商品 SPU 不存在");
    ErrorCode SPU_SAVE_FAIL_CATEGORY_LEVEL_ERROR = new ErrorCode(1_008_005_001, "商品分类不正确，原因：必须使用第二级的商品分类及以下");
    ErrorCode SPU_SAVE_FAIL_COUPON_TEMPLATE_NOT_EXISTS = new ErrorCode(1_008_005_002, "商品 SPU 保存失败，原因：优惠劵不存在");
    ErrorCode SPU_NOT_ENABLE = new ErrorCode(1_008_005_003, "商品 SPU【{}】不处于上架状态");
    ErrorCode SPU_NOT_RECYCLE = new ErrorCode(1_008_005_004, "商品 SPU 不处于回收站状态");

    // ========== 商品 SKU 1-008-006-000 ==========
    ErrorCode SKU_NOT_EXISTS = new ErrorCode(1_008_006_000, "商品 SKU 不存在");
    ErrorCode SKU_PROPERTIES_DUPLICATED = new ErrorCode(1_008_006_001, "商品 SKU 的属性组合存在重复");
    ErrorCode SPU_ATTR_NUMBERS_MUST_BE_EQUALS = new ErrorCode(1_008_006_002, "一个 SPU 下的每个 SKU，其属性项必须一致");
    ErrorCode SPU_SKU_NOT_DUPLICATE = new ErrorCode(1_008_006_003, "一个 SPU 下的每个 SKU，必须不重复");
    ErrorCode SKU_STOCK_NOT_ENOUGH = new ErrorCode(1_008_006_004, "商品 SKU 库存不足");

    // ========== 商品 评价 1-008-007-000 ==========
    ErrorCode COMMENT_NOT_EXISTS = new ErrorCode(1_008_007_000, "商品评价不存在");
    ErrorCode COMMENT_ORDER_EXISTS = new ErrorCode(1_008_007_001, "订单的商品评价已存在");

    // ========== 商品 收藏 1-008-008-000 ==========
    ErrorCode FAVORITE_EXISTS = new ErrorCode(1_008_008_000, "该商品已经被收藏");
    ErrorCode FAVORITE_NOT_EXISTS = new ErrorCode(1_008_008_001, "商品收藏不存在");

    // ========== 商品 筛选维度 1-008-009-000 ==========
    ErrorCode FILTER_DIMENSION_NOT_EXISTS = new ErrorCode(1_008_009_000, "商品筛选维度不存在");
    /**
     * ⚠️ 消息里**必须带上分类名**：重名检测查的是**整条分类祖先链**（Q10=B），
     * 所以运营看到"维度【宽度】已存在"会找不到该去哪改 —— 得告诉他是**哪个分类上的**那个。
     */
    ErrorCode FILTER_DIMENSION_NAME_DUPLICATE = new ErrorCode(1_008_009_001, "分类【{}】下已存在筛选维度【{}】");
    ErrorCode FILTER_VALUE_NOT_EXISTS = new ErrorCode(1_008_009_002, "筛选维度可选值不存在");
    ErrorCode FILTER_VALUE_NAME_DUPLICATE = new ErrorCode(1_008_009_003, "筛选维度【{}】下已存在可选值【{}】");
    /** 名称不能只有空白 —— VO 上的 @NotEmpty 拦不住 " " 这种 */
    ErrorCode FILTER_DIMENSION_NAME_EMPTY = new ErrorCode(1_008_009_004, "筛选维度名称不能为空白");
    ErrorCode FILTER_VALUE_NAME_EMPTY = new ErrorCode(1_008_009_005, "可选值名称不能为空白");
    /**
     * 同一维度传了多个值。
     *
     * 前台是**单选**（Q9=B），所以这条请求不合法。**必须明确拒绝**，
     * 不能静默取一个 —— 前端一旦有 bug，静默取值的表现是"筛选结果莫名其妙"，无从排查。
     */
    ErrorCode FILTER_VALUE_MULTI_IN_DIMENSION = new ErrorCode(1_008_009_006, "筛选维度【{}】下只能选择 1 个值");

    //========== MaxLabel 会员授权商品 1-008-010-000 ==========
    ErrorCode MEMBER_SUBSCRIPTION_PLAN_NOT_EXISTS = new ErrorCode(1_008_010_000, "会员授权商品计划不存在");
    ErrorCode MEMBER_SUBSCRIPTION_PLAN_DUPLICATE = new ErrorCode(1_008_010_001, "该 SKU 已配置会员授权计划");
    ErrorCode MEMBER_SUBSCRIPTION_PLAN_PRICE_ZERO = new ErrorCode(1_008_010_002, "会员授权 SKU 价格必须大于 0");
    ErrorCode MEMBER_SUBSCRIPTION_PLAN_NOT_VIRTUAL = new ErrorCode(1_008_010_003, "会员授权商品必须是虚拟商品");
    ErrorCode MEMBER_SUBSCRIPTION_PLAN_MULTI_SPU = new ErrorCode(1_008_010_004, "同一租户只能配置一个启用的会员商品 SPU");
    ErrorCode MEMBER_SUBSCRIPTION_PLAN_INVALID_PERIOD = new ErrorCode(1_008_010_005, "会员授权周期必须在 1-120 个月之间");

    //========== MaxLabel 企业商品 1-008-011-000 ==========
    ErrorCode SPU_ENTERPRISE_ONLY = new ErrorCode(1_008_011_000, "该商品仅对企业客户开放");
    ErrorCode ENTERPRISE_PLAN_FORBIDDEN = new ErrorCode(1_008_011_001, "仅企业管理员可购买企业授权");
    ErrorCode MEMBER_SUBSCRIPTION_PLAN_SCOPE_INVALID = new ErrorCode(1_008_011_002, "会员授权商品类别不合法");
    ErrorCode MEMBER_SUBSCRIPTION_PLAN_SCOPE_SPU_MISMATCH = new ErrorCode(1_008_011_003, "计划类别与商品企业专享标记不一致");
    ErrorCode SPU_SUBSCRIPTION_ONLY = new ErrorCode(1_008_011_004, "该商品是会员授权商品，只能从会员授权页或企业采购页进入");

}
