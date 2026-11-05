package cn.iocoder.yudao.module.member.dal.mysql.share;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareContentDO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MaxLabelShareContentMapper extends BaseMapperX<MaxLabelShareContentDO> {

    default PageResult<MaxLabelShareContentDO> selectPersonalPage(Long memberId, String keyword,
                                                                  Integer contentType, PageParam pageParam) {
        LambdaQueryWrapperX<MaxLabelShareContentDO> wrapper = new LambdaQueryWrapperX<MaxLabelShareContentDO>()
                .eq(MaxLabelShareContentDO::getScope, 1)
                .eq(MaxLabelShareContentDO::getOwnerMemberId, memberId)
                .eqIfPresent(MaxLabelShareContentDO::getContentType, contentType);
        appendKeyword(wrapper, keyword);
        return selectPage(pageParam, wrapper.orderByDesc(MaxLabelShareContentDO::getCreateTime)
                .orderByDesc(MaxLabelShareContentDO::getId));
    }

    default PageResult<MaxLabelShareContentDO> selectEnterprisePage(Long orgId, Long folderId, String keyword,
                                                                    Integer contentType, PageParam pageParam) {
        LambdaQueryWrapperX<MaxLabelShareContentDO> wrapper = new LambdaQueryWrapperX<MaxLabelShareContentDO>()
                .eq(MaxLabelShareContentDO::getScope, 2)
                .eq(MaxLabelShareContentDO::getOrgId, orgId)
                .eq(MaxLabelShareContentDO::getFolderId, folderId)
                .eqIfPresent(MaxLabelShareContentDO::getContentType, contentType);
        appendKeyword(wrapper, keyword);
        return selectPage(pageParam, wrapper.orderByDesc(MaxLabelShareContentDO::getCreateTime)
                .orderByDesc(MaxLabelShareContentDO::getId));
    }

    default MaxLabelShareContentDO selectByIdAndScope(Long id, Integer scope) {
        return selectOne(new LambdaQueryWrapperX<MaxLabelShareContentDO>()
                .eq(MaxLabelShareContentDO::getId, id)
                .eq(MaxLabelShareContentDO::getScope, scope));
    }

    /** 本人上传到企业的全部内容，不依赖文件夹可见性（FR-409）。 */
    default PageResult<MaxLabelShareContentDO> selectMyEnterprisePage(Long memberId, PageParam pageParam) {
        return selectPage(pageParam, new LambdaQueryWrapperX<MaxLabelShareContentDO>()
                .eq(MaxLabelShareContentDO::getScope, 2)
                .eq(MaxLabelShareContentDO::getOwnerMemberId, memberId)
                .orderByDesc(MaxLabelShareContentDO::getCreateTime)
                .orderByDesc(MaxLabelShareContentDO::getId));
    }

    default long sumSizeByPersonal(Long memberId) {
        return sumSize(new QueryWrapper<MaxLabelShareContentDO>()
                .select("size_bytes")
                .eq("scope", 1)
                .eq("owner_member_id", memberId));
    }

    default long sumSizeByOrg(Long orgId) {
        return sumSize(new QueryWrapper<MaxLabelShareContentDO>()
                .select("size_bytes")
                .eq("scope", 2)
                .eq("org_id", orgId));
    }

    default long sumSizeByFolder(Long folderId) {
        return sumSize(new QueryWrapper<MaxLabelShareContentDO>()
                .select("size_bytes")
                .eq("folder_id", folderId));
    }

    default int countByFolder(Long folderId) {
        return selectCount(new LambdaQueryWrapperX<MaxLabelShareContentDO>()
                .eq(MaxLabelShareContentDO::getFolderId, folderId)).intValue();
    }

    default List<MaxLabelShareContentDO> selectListByFolder(Long folderId) {
        return selectList(new LambdaQueryWrapperX<MaxLabelShareContentDO>()
                .eq(MaxLabelShareContentDO::getFolderId, folderId));
    }

    private long sumSize(QueryWrapper<MaxLabelShareContentDO> wrapper) {
        return selectObjs(wrapper).stream()
                .filter(java.util.Objects::nonNull)
                .mapToLong(value -> ((Number) value).longValue())
                .sum();
    }

    private static void appendKeyword(LambdaQueryWrapperX<MaxLabelShareContentDO> wrapper, String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return;
        }
        wrapper.and(query -> query.like(MaxLabelShareContentDO::getName, keyword)
                .or().like(MaxLabelShareContentDO::getCategory, keyword)
                .or().like(MaxLabelShareContentDO::getKeywords, keyword));
    }

}
