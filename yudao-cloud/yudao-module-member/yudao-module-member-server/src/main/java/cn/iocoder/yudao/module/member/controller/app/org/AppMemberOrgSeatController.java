package cn.iocoder.yudao.module.member.controller.app.org;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.member.controller.app.org.vo.AppMemberOrgMemberIdReqVO;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgSeatService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - MaxLabel 企业席位")
@RestController
@RequestMapping("/member/org/seat")
@Validated
public class AppMemberOrgSeatController {

    private static final int OPERATOR_MEMBER = 1;

    @Resource
    private MaxLabelOrgService orgService;
    @Resource
    private MaxLabelOrgSeatService seatService;

    @PostMapping("/assign")
    @Operation(summary = "分配席位")
    public CommonResult<Boolean> assign(@Valid @RequestBody AppMemberOrgMemberIdReqVO reqVO) {
        Long operatorId = SecurityFrameworkUtils.getLoginUserId();
        MemberOrgRespDTO org = orgService.requireOrgAdmin(operatorId);
        seatService.assign(org.getOrgId(), reqVO.getMemberId(), OPERATOR_MEMBER, operatorId);
        return success(true);
    }

    @PostMapping("/revoke")
    @Operation(summary = "撤销席位")
    public CommonResult<Boolean> revoke(@Valid @RequestBody AppMemberOrgMemberIdReqVO reqVO) {
        Long operatorId = SecurityFrameworkUtils.getLoginUserId();
        MemberOrgRespDTO org = orgService.requireOrgAdmin(operatorId);
        seatService.revoke(org.getOrgId(), reqVO.getMemberId(), OPERATOR_MEMBER, operatorId);
        return success(true);
    }

}
