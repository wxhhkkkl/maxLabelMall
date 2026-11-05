package cn.iocoder.yudao.module.member.service.share;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelPlatformContentCategoryDO;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelPlatformContentCategoryMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.*;

@Service
@Validated
public class PlatformContentCategoryServiceImpl implements PlatformContentCategoryService {

    @Resource
    private MaxLabelPlatformContentCategoryMapper categoryMapper;

    @Override
    public List<MaxLabelPlatformContentCategoryDO> getList() {
        return categoryMapper.selectListOrdered();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(String name, Integer sort) {
        String value = StrUtil.trim(name);
        if (StrUtil.isBlank(value) || value.length() > 64) {
            throw exception(MEMBER_SHARE_CONTENT_INVALID);
        }
        if (categoryMapper.selectByName(value) != null) {
            throw exception(MEMBER_PLATFORM_CONTENT_CATEGORY_EXISTS);
        }
        MaxLabelPlatformContentCategoryDO category = MaxLabelPlatformContentCategoryDO.builder()
                .name(value)
                .sort(sort == null ? 0 : sort)
                .build();
        categoryMapper.insert(category);
        return category.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null || categoryMapper.selectById(id) == null) {
            throw exception(MEMBER_PLATFORM_CONTENT_CATEGORY_NOT_FOUND);
        }
        // 只删分类本身；已有内容的 category 文本保留，不做级联修改。
        categoryMapper.deleteById(id);
    }

}
