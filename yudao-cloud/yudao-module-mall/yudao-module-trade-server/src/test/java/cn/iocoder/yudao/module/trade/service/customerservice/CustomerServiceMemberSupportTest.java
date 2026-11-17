package cn.iocoder.yudao.module.trade.service.customerservice;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMER_SERVICE_MEMBER_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 客户服务会员检索（021 / R6 / FR-582）
 */
@ExtendWith(MockitoExtension.class)
public class CustomerServiceMemberSupportTest {

    @Mock
    private MemberUserApi memberUserApi;

    @InjectMocks
    private CustomerServiceMemberSupport memberSupport;

    private MemberUserRespDTO user(Long id, String nickname, String mobile) {
        MemberUserRespDTO dto = new MemberUserRespDTO();
        dto.setId(id);
        dto.setNickname(nickname);
        dto.setMobile(mobile);
        return dto;
    }

    @Test
    public void testValidateMember_missing() {
        when(memberUserApi.getUser(99L)).thenReturn(CommonResult.success(null));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> memberSupport.validateMember(99L));
        assertEquals(CUSTOMER_SERVICE_MEMBER_NOT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    public void testSearch_byMobile_exactMatch() {
        MemberUserRespDTO dto = user(88L, "张三", "13800008888");
        when(memberUserApi.getUserByMobile("13800008888")).thenReturn(CommonResult.success(dto));

        List<MemberUserRespDTO> list = memberSupport.search("13800008888");

        assertEquals(1, list.size());
        assertEquals(88L, list.get(0).getId());
    }

    @Test
    public void testSearch_byNickname_fuzzy() {
        when(memberUserApi.getUserListByNickname("张")).thenReturn(
                CommonResult.success(List.of(user(88L, "张三", "13800008888"))));

        List<MemberUserRespDTO> list = memberSupport.search("张");

        assertEquals(1, list.size());
        assertEquals("张三", list.get(0).getNickname());
    }

    @Test
    public void testSearch_blank_returnsEmpty() {
        assertEquals(0, memberSupport.search(" ").size());
    }

    @Test
    public void testSearch_mobileNotFound_returnsEmpty() {
        when(memberUserApi.getUserByMobile(anyString())).thenReturn(CommonResult.success(null));
        assertEquals(0, memberSupport.search("13800009999").size());
    }

}
