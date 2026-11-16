package cn.iocoder.yudao.module.product.service.spu;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.dal.mysql.spu.ProductSpuMapper;
import cn.iocoder.yudao.module.product.enums.spu.ProductSpuBrowseLevelTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.SPU_NOT_EXISTS;

/**
 * 商品浏览等级 Service 实现类（019）
 *
 * 唯一判定来源：公开 / 全部登录会员 / 指定等级会员。
 *
 * @author MaxLabel
 */
@Service
@Validated
public class ProductSpuBrowseLevelServiceImpl implements ProductSpuBrowseLevelService {

    @Resource
    private MemberUserApi memberUserApi;
    @Resource
    private ProductSpuMapper productSpuMapper;

    @Override
    public Long getViewerLevelId(Long userId) {
        if (userId == null) {
            return null;
        }
        MemberUserRespDTO user = memberUserApi.getUser(userId).getCheckedData();
        if (user == null || user.getLevelId() == null || user.getLevelId() == 0L) {
            return null;
        }
        return user.getLevelId();
    }

    @Override
    public boolean isVisible(ProductSpuDO spu, Long userId) {
        return isVisible(spu, userId, getViewerLevelId(userId));
    }

    @Override
    public boolean isVisible(ProductSpuDO spu, Long userId, Long levelId) {
        if (spu == null) {
            return false;
        }
        Integer type = spu.getBrowseLevelType();
        // null 与 SQL 条件保持一致：不算公开（生产库 NOT NULL，理论上不会出现）
        if (type == null) {
            return false;
        }
        if (ProductSpuBrowseLevelTypeEnum.PUBLIC.getType().equals(type)) {
            return true;
        }
        if (ProductSpuBrowseLevelTypeEnum.LOGIN.getType().equals(type)) {
            return userId != null;
        }
        if (ProductSpuBrowseLevelTypeEnum.LEVEL.getType().equals(type)) {
            if (userId == null || levelId == null) {
                return false;
            }
            return CollUtil.isNotEmpty(spu.getBrowseLevelIds())
                    && spu.getBrowseLevelIds().contains(levelId);
        }
        // 未知类型 fail-closed
        return false;
    }

    @Override
    public void validateVisible(ProductSpuDO spu) {
        if (!isVisible(spu, getLoginUserId())) {
            throw exception(SPU_NOT_EXISTS);
        }
    }

    @Override
    public List<Long> filterVisibleSpuIds(Long userId, Collection<Long> spuIds) {
        if (CollUtil.isEmpty(spuIds)) {
            return Collections.emptyList();
        }
        List<ProductSpuDO> spus = productSpuMapper.selectByIds(spuIds);
        if (CollUtil.isEmpty(spus)) {
            return Collections.emptyList();
        }
        Long levelId = getViewerLevelId(userId);
        return spus.stream()
                .filter(spu -> isVisible(spu, userId, levelId))
                .map(ProductSpuDO::getId)
                .toList();
    }

}
