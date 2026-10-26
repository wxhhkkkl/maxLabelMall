package cn.iocoder.yudao.module.member.api.org;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgLicenseGrantReqDTO;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.member.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - MaxLabel 企业")
public interface MemberOrgApi {

    String PREFIX = ApiConstants.PREFIX + "/org";

    @GetMapping(PREFIX + "/get-by-member-id")
    @Operation(summary = "获得会员所属企业")
    @Parameter(name = "memberId", description = "会员编号", required = true)
    CommonResult<MemberOrgRespDTO> getByMemberId(@RequestParam("memberId") Long memberId);

    @PostMapping(PREFIX + "/grant-license")
    @Operation(summary = "发放企业授权批次（幂等）")
    CommonResult<Boolean> grantLicense(@RequestBody MemberOrgLicenseGrantReqDTO reqDTO);

}
