package cn.iocoder.yudao.module.member.api.org;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgLicenseGrantReqDTO;
import cn.iocoder.yudao.module.member.api.org.dto.MemberOrgRespDTO;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgLicenseService;
import cn.iocoder.yudao.module.member.service.org.MaxLabelOrgService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
public class MemberOrgApiImpl implements MemberOrgApi {

    @Resource
    private MaxLabelOrgService orgService;
    @Resource
    private MaxLabelOrgLicenseService licenseService;

    @Override
    public CommonResult<MemberOrgRespDTO> getByMemberId(Long memberId) {
        return success(orgService.getMemberOrg(memberId));
    }

    @Override
    public CommonResult<Boolean> grantLicense(MemberOrgLicenseGrantReqDTO reqDTO) {
        licenseService.grant(reqDTO);
        return success(true);
    }

}
