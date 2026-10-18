package cn.iocoder.yudao.module.pay.controller.app.channel;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.pay.service.channel.PayChannelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

@Tag(name = "用户 App - 支付渠道")
@RestController
@RequestMapping("/pay/channel")
@Validated
public class AppPayChannelController {

    @Resource
    private PayChannelService channelService;

    @GetMapping("/get-enable-code-list")
    @Operation(summary = "获得指定应用的开启的支付渠道编码列表")
    @Parameter(name = "appId", description = "应用编号", required = true, example = "1")
    public CommonResult<Set<String>> getEnableChannelCodeList(@RequestParam("appId") Long appId) {
        List<PayChannelDO> channels = channelService.getEnableChannelList(appId);
        // ⚠️ 008：**「对公转账」（offline）不是"支付页能选的渠道"** —— 它是**下单时**选的
        //     （下单即进「待收款」、不建在线支付单，订单详情也不给任何在线支付入口）。
        //     它之所以在 `pay_channel` 里是启用的，是因为**退款**要按渠道取 PayClient
        //     （`PayRefundServiceImpl`）以及支付单的渠道名要能查到 —— 与该列表无关。
        //     不滤掉的话，客户在**普通待支付订单**的支付页会看到一个名叫「offline」的渠道，
        //     而它一旦被提交必然失败（`OfflinePayClient.doUnifiedOrder` 直接抛异常）。
        return success(convertSet(channels, PayChannelDO::getCode).stream()
                .filter(code -> !PayChannelEnum.OFFLINE.getCode().equals(code))
                .collect(Collectors.toSet()));
    }

}
