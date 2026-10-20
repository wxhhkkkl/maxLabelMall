package cn.iocoder.yudao.module.member.controller.admin.subscription;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.member.controller.admin.subscription.vo.MemberSubscriptionPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.subscription.vo.MemberSubscriptionRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.subscription.MemberSubscriptionDO;
import cn.iocoder.yudao.module.member.service.subscription.MemberSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - MaxLabel 会员订阅")
@RestController
@RequestMapping("/member/subscription")
@Validated
public class MemberSubscriptionController {

    @Resource
    private MemberSubscriptionService subscriptionService;

    @GetMapping("/page")
    @Operation(summary = "获得会员订阅分页")
    @PreAuthorize("@ss.hasPermission('member:subscription:query')")
    public CommonResult<PageResult<MemberSubscriptionRespVO>> getPage(@Valid MemberSubscriptionPageReqVO reqVO) {
        PageResult<MemberSubscriptionDO> page = subscriptionService.getPageByMemberId(reqVO.getUserId(), reqVO);
        List<MemberSubscriptionRespVO> list = page.getList().stream().map(this::toRespVO).toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

    private MemberSubscriptionRespVO toRespVO(MemberSubscriptionDO row) {
        MemberSubscriptionRespVO vo = BeanUtils.toBean(row, MemberSubscriptionRespVO.class);
        LocalDate endDate = row.getEndTime().toLocalDate().minusDays(1);
        vo.setEndDate(endDate);
        vo.setStartDate(row.getStartTime().toLocalDate());
        vo.setActive(row.getEndTime().isAfter(LocalDateTime.now()));
        vo.setRemainingDays(Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), endDate)));
        return vo;
    }

}
