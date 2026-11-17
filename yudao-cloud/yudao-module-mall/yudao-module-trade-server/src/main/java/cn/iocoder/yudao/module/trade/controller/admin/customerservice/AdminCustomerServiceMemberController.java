package cn.iocoder.yudao.module.trade.controller.admin.customerservice;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.trade.controller.admin.customerservice.vo.AdminCustomerServiceMemberRespVO;
import cn.iocoder.yudao.module.trade.service.customerservice.CustomerServiceMemberSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 客户服务会员检索（021 / US75）
 */
@Tag(name = "管理后台 - 客户服务会员检索")
@RestController
@RequestMapping("/trade/customer-service/member")
@Validated
public class AdminCustomerServiceMemberController {

    @Resource
    private CustomerServiceMemberSupport customerServiceMemberSupport;

    @GetMapping("/search")
    @Operation(summary = "检索会员（代建工单用）")
    @Parameter(name = "keyword", required = true, description = "11 位手机号精准 / 昵称模糊")
    @PreAuthorize("@ss.hasPermission('trade:customer-service:handle')")
    public CommonResult<List<AdminCustomerServiceMemberRespVO>> search(@RequestParam("keyword") String keyword) {
        List<MemberUserRespDTO> members = customerServiceMemberSupport.search(keyword);
        return success(members.stream().map(member -> {
            AdminCustomerServiceMemberRespVO vo = new AdminCustomerServiceMemberRespVO();
            vo.setId(member.getId());
            vo.setNickname(member.getNickname());
            vo.setMobile(member.getMobile());
            return vo;
        }).toList());
    }

}
