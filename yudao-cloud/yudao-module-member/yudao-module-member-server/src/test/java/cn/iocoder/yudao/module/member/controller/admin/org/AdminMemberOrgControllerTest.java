package cn.iocoder.yudao.module.member.controller.admin.org;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.org.vo.MaxLabelOrgPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.org.vo.MaxLabelOrgSaveReqVO;
import cn.iocoder.yudao.module.member.controller.admin.org.vo.MaxLabelOrgStatusReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgLicenseService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgMemberService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgSeatService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_ORG_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminMemberOrgControllerTest {

    @Mock
    private MaxLabelOrgService orgService;
    @Mock
    private MaxLabelOrgMemberService memberService;
    @Mock
    private MaxLabelOrgSeatService seatService;
    @Mock
    private MaxLabelOrgLicenseService licenseService;
    @Mock
    private MemberUserMapper memberUserMapper;
    @InjectMocks
    private AdminMemberOrgController controller;

    private MaxLabelOrgDO org() {
        return MaxLabelOrgDO.builder().id(88L).name("测试企业").ownerMemberId(1001L)
                .status(0).remark("").build();
    }

    @Test
    void page_mapsOrgWithOwnerAndCapacity() {
        when(orgService.getOrgPage(any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(org()), 1L));
        when(memberUserMapper.selectById(1001L)).thenReturn(
                MemberUserDO.builder().id(1001L).nickname("张三").mobile("13800138000").build());
        when(memberService.getMemberCount(88L)).thenReturn(5);
        when(licenseService.getSeatTotal(88L)).thenReturn(20);
        when(seatService.getActiveCount(88L)).thenReturn(3);
        when(licenseService.getMaxEndTime(88L))
                .thenReturn(LocalDateTime.now().plusMonths(12));

        var page = controller.getPage(new MaxLabelOrgPageReqVO()).getData();
        assertEquals(1L, page.getTotal());
        assertEquals("测试企业", page.getList().get(0).getName());
        assertEquals("张三", page.getList().get(0).getOwnerNickname());
        assertEquals("138****8000", page.getList().get(0).getOwnerMobile());
        assertEquals(20, page.getList().get(0).getSeatTotal());
        assertEquals(3, page.getList().get(0).getSeatUsed());
    }

    @Test
    void create_delegates() {
        MaxLabelOrgSaveReqVO req = new MaxLabelOrgSaveReqVO();
        req.setName("测试企业");
        req.setOwnerMemberId(1001L);
        when(orgService.createOrg("测试企业", 1001L, null)).thenReturn(88L);
        assertEquals(88L, controller.create(req).getData());
    }

    @Test
    void updateAndStatus_delegate() {
        MaxLabelOrgSaveReqVO req = new MaxLabelOrgSaveReqVO();
        req.setId(88L);
        req.setName("新名称");
        req.setOwnerMemberId(1001L);
        MaxLabelOrgStatusReqVO statusReq = new MaxLabelOrgStatusReqVO();
        statusReq.setId(88L);
        statusReq.setStatus(1);

        controller.update(req);
        controller.updateStatus(statusReq);
        verify(orgService).updateOrg(88L, "新名称", null);
        verify(orgService).updateStatus(88L, 1);
    }

    @Test
    void get_missingOrgReturnsBusinessError() {
        when(orgService.getOrg(99L)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class, () -> controller.get(99L));
        assertEquals(MEMBER_ORG_NOT_EXISTS.getCode(), exception.getCode());
    }

}
