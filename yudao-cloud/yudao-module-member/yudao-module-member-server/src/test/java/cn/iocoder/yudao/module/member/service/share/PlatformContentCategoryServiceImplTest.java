package cn.iocoder.yudao.module.member.service.share;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelPlatformContentCategoryDO;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelPlatformContentCategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_PLATFORM_CONTENT_CATEGORY_EXISTS;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_PLATFORM_CONTENT_CATEGORY_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PlatformContentCategoryServiceImplTest {

    @Mock private MaxLabelPlatformContentCategoryMapper categoryMapper;
    @InjectMocks private PlatformContentCategoryServiceImpl service;

    @Test
    void createTrimsName() {
        doAnswer(invocation -> {
            MaxLabelPlatformContentCategoryDO category = invocation.getArgument(0);
            category.setId(9L);
            return 1;
        }).when(categoryMapper).insert(any(MaxLabelPlatformContentCategoryDO.class));

        assertEquals(9L, service.create("  物流  ", null));
        org.mockito.ArgumentCaptor<MaxLabelPlatformContentCategoryDO> captor =
                org.mockito.ArgumentCaptor.forClass(MaxLabelPlatformContentCategoryDO.class);
        verify(categoryMapper).insert(captor.capture());
        assertEquals("物流", captor.getValue().getName());
    }

    @Test
    void duplicateNameRejected() {
        when(categoryMapper.selectByName("物流")).thenReturn(
                MaxLabelPlatformContentCategoryDO.builder().id(1L).name("物流").build());
        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.create("物流", null));
        assertEquals(MEMBER_PLATFORM_CONTENT_CATEGORY_EXISTS.getCode(), exception.getCode());
    }

    @Test
    void deleteMissingRejected() {
        when(categoryMapper.selectById(9L)).thenReturn(null);
        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.delete(9L));
        assertEquals(MEMBER_PLATFORM_CONTENT_CATEGORY_NOT_FOUND.getCode(), exception.getCode());
    }

}
