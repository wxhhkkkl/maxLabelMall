package cn.iocoder.yudao.module.product.dal.mysql.spu;

import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.mybatis.core.type.IntegerListTypeHandler;
import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.enums.ProductConstants;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.handlers.Jackson3TypeHandler;
import org.apache.ibatis.annotations.*;

import java.util.Objects;
import java.util.Set;

@Mapper
public interface ProductSpuMapper extends BaseMapperX<ProductSpuDO> {

    /**
     * 查询商品 SPU（包含已删除）
     * 注意：使用 @Results 手动指定 typeHandler，否则 @Select 不会应用 autoResultMap，sliderPicUrls，deliveryTypes 字段无法解析 JSON
     */
    @Select("SELECT * FROM product_spu WHERE id = #{id}")
    @Results({
            @Result(column = "slider_pic_urls", property = "sliderPicUrls", typeHandler = Jackson3TypeHandler.class),
            @Result(column = "delivery_types", property = "deliveryTypes", typeHandler = IntegerListTypeHandler.class),
    })
    ProductSpuDO selectByIdIncludeDeleted(@Param("id") Long id);

    /**
     * 获取商品 SPU 分页列表数据
     *
     * @param reqVO 分页请求参数
     * @return 商品 SPU 分页列表数据
     */
    default PageResult<ProductSpuDO> selectPage(ProductSpuPageReqVO reqVO) {
        Integer tabType = reqVO.getTabType();
        LambdaQueryWrapperX<ProductSpuDO> queryWrapper = new LambdaQueryWrapperX<ProductSpuDO>()
                .likeIfPresent(ProductSpuDO::getName, reqVO.getName())
                .eqIfPresent(ProductSpuDO::getCategoryId, reqVO.getCategoryId())
                .betweenIfPresent(ProductSpuDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(ProductSpuDO::getSort)
                .orderByDesc(ProductSpuDO::getId);
        appendTabQuery(tabType, queryWrapper);
        return selectPage(reqVO, queryWrapper);
    }

    /**
     * 查询触发警戒库存的 SPU 数量
     *
     * @return 触发警戒库存的 SPU 数量
     */
    default Long selectCount() {
        LambdaQueryWrapperX<ProductSpuDO> queryWrapper = new LambdaQueryWrapperX<>();
        // 库存小于等于警戒库存
        queryWrapper.le(ProductSpuDO::getStock, ProductConstants.ALERT_STOCK)
                // 如果库存触发警戒库存且状态为回收站的话则不计入触发警戒库存的个数
                .notIn(ProductSpuDO::getStatus, ProductSpuStatusEnum.RECYCLE.getStatus());
        return selectCount(queryWrapper);
    }

    /**
     * 获得商品 SPU 分页，提供给用户 App 使用
     */
    default PageResult<ProductSpuDO> selectPage(AppProductSpuPageReqVO pageReqVO, Set<Long> categoryIds) {
        LambdaQueryWrapperX<ProductSpuDO> query = new LambdaQueryWrapperX<ProductSpuDO>()
                // 关键字匹配，目前只匹配商品名
                .likeIfPresent(ProductSpuDO::getName, pageReqVO.getKeyword())
                // 分类
                .inIfPresent(ProductSpuDO::getCategoryId, categoryIds);
        // 上架状态 且有库存
        query.eq(ProductSpuDO::getStatus, ProductSpuStatusEnum.ENABLE.getStatus());
        // 企业专享商品不进入公开商城（012 / Q72）
        query.and(w -> w.isNull(ProductSpuDO::getEnterpriseOnly)
                .or().eq(ProductSpuDO::getEnterpriseOnly, false));
        // 会员授权商品只在个人续费 / 企业采购页销售，不进入公开商城列表。
        // 判定来源是授权计划表本身（而不是新增商品标志位），计划增删时自动同步。
        // ⚠️ 子查询是裸 SQL，逻辑删除与租户条件都要自己写。
        query.notExists("SELECT 1 FROM maxlabel_subscription_plan msp"
                + " WHERE msp.spu_id = product_spu.id"
                + " AND msp.tenant_id = product_spu.tenant_id"
                + " AND msp.deleted = 0");

        // 筛选维度（特性 003 / FR-101）：
        // **每个已选值一条等值 EXISTS**，多条之间自然就是 AND。
        // Q9=B 是单选 —— 一个维度至多一个值，所以"每个值一条"等价于"每个已选维度一条"。
        // 不传时一条都不追加：这个接口是全站商品列表共用的，多的条件可能波及别处。
        //
        // ⚠️ 子查询是**裸 SQL**，MyBatis-Plus 的逻辑删除**不会**自动注入，
        //    所以必须自己写 `pfv.deleted = 0`。
        if (CollUtil.isNotEmpty(pageReqVO.getFilterValueIds())) {
            for (Long filterValueId : pageReqVO.getFilterValueIds()) {
                query.exists("SELECT 1 FROM product_spu_filter_value pfv"
                        + " WHERE pfv.spu_id = product_spu.id"
                        + " AND pfv.filter_value_id = {0} AND pfv.deleted = 0", filterValueId);
            }
        }

        // 排序逻辑
        if (Objects.equals(pageReqVO.getSortField(), AppProductSpuPageReqVO.SORT_FIELD_SALES_COUNT)) {
            query.last(String.format(" ORDER BY (sales_count + virtual_sales_count) %s, sort DESC, id DESC",
                    pageReqVO.getSortAsc() ? "ASC" : "DESC"));
        } else if (Objects.equals(pageReqVO.getSortField(), AppProductSpuPageReqVO.SORT_FIELD_PRICE)) {
            query.orderBy(true, pageReqVO.getSortAsc(), ProductSpuDO::getPrice)
                    .orderByDesc(ProductSpuDO::getSort).orderByDesc(ProductSpuDO::getId);
        } else if (Objects.equals(pageReqVO.getSortField(), AppProductSpuPageReqVO.SORT_FIELD_CREATE_TIME)) {
            query.orderBy(true, pageReqVO.getSortAsc(), ProductSpuDO::getCreateTime)
                    .orderByDesc(ProductSpuDO::getSort).orderByDesc(ProductSpuDO::getId);
        } else {
            query.orderByDesc(ProductSpuDO::getSort).orderByDesc(ProductSpuDO::getId);
        }
        return selectPage(pageReqVO, query);
    }

    /**
     * 更新商品 SPU 库存
     *
     * @param id        商品 SPU 编号
     * @param incrCount 增加的库存数量
     */
    default void updateStock(Long id, Integer incrCount) {
        // 拼接 SQL
        if (incrCount == 0) {
            return;
        }
        String sql;
        if (incrCount > 0) {
            sql = " stock = stock + " + incrCount + ", sales_count = sales_count - " + incrCount;
        } else {
            sql = " stock = stock - " + Math.abs(incrCount) + ", sales_count = sales_count + " + Math.abs(incrCount);
        }
        // 执行更新
        LambdaUpdateWrapper<ProductSpuDO> updateWrapper = new LambdaUpdateWrapper<ProductSpuDO>()
                .setSql(sql)
                .eq(ProductSpuDO::getId, id);
        update(null, updateWrapper);
    }

    /**
     * 添加后台 Tab 选项的查询条件
     *
     * @param tabType 标签类型
     * @param query   查询条件
     */
    static void appendTabQuery(Integer tabType, LambdaQueryWrapperX<ProductSpuDO> query) {
        // 出售中商品
        if (ObjectUtil.equals(ProductSpuPageReqVO.FOR_SALE, tabType)) {
            query.eqIfPresent(ProductSpuDO::getStatus, ProductSpuStatusEnum.ENABLE.getStatus());
        }
        // 仓储中商品
        if (ObjectUtil.equals(ProductSpuPageReqVO.IN_WAREHOUSE, tabType)) {
            query.eqIfPresent(ProductSpuDO::getStatus, ProductSpuStatusEnum.DISABLE.getStatus());
        }
        // 已售空商品
        if (ObjectUtil.equals(ProductSpuPageReqVO.SOLD_OUT, tabType)) {
            query.eqIfPresent(ProductSpuDO::getStock, 0);
        }
        // 警戒库存
        if (ObjectUtil.equals(ProductSpuPageReqVO.ALERT_STOCK, tabType)) {
            query.le(ProductSpuDO::getStock, ProductConstants.ALERT_STOCK)
                    // 如果库存触发警戒库存且状态为回收站的话则不在警戒库存列表展示
                    .notIn(ProductSpuDO::getStatus, ProductSpuStatusEnum.RECYCLE.getStatus());
        }
        // 回收站
        if (ObjectUtil.equals(ProductSpuPageReqVO.RECYCLE_BIN, tabType)) {
            query.eqIfPresent(ProductSpuDO::getStatus, ProductSpuStatusEnum.RECYCLE.getStatus());
        }
    }

    /**
     * 按筛选条件 + Tab 统计商品 SPU 数量（用于带筛选的 tab count）
     *
     * @param reqVO   筛选条件（name/categoryId/createTime）
     * @param tabType Tab 标签类型
     * @return 数量
     */
    default Long selectCountByTab(ProductSpuPageReqVO reqVO, Integer tabType) {
        LambdaQueryWrapperX<ProductSpuDO> queryWrapper = new LambdaQueryWrapperX<ProductSpuDO>()
                .likeIfPresent(ProductSpuDO::getName, reqVO.getName())
                .eqIfPresent(ProductSpuDO::getCategoryId, reqVO.getCategoryId())
                .betweenIfPresent(ProductSpuDO::getCreateTime, reqVO.getCreateTime());
        appendTabQuery(tabType, queryWrapper);
        return selectCount(queryWrapper);
    }

    /**
     * 更新商品 SPU 浏览量
     *
     * @param id        商品 SPU 编号
     * @param incrCount 增加的数量
     */
    default void updateBrowseCount(Long id, int incrCount) {
        LambdaUpdateWrapper<ProductSpuDO> updateWrapper = new LambdaUpdateWrapper<ProductSpuDO>()
                .setSql(" browse_count = browse_count +" + incrCount)
                .eq(ProductSpuDO::getId, id);
        update(null, updateWrapper);
    }

}
