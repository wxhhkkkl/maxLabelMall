package cn.iocoder.yudao.module.trade.controller.app.invoice;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceTitleRespVO;
import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceTitleSaveReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.invoice.TradeInvoiceTitleDO;
import cn.iocoder.yudao.module.trade.service.invoice.TradeInvoiceTitleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 发票抬头簿（C 端 / US23）
 *
 * ⚠️ **四个接口一律按当前登录会员**：会员身份从登录态取，**不接受请求传入** ——
 * 传别人的 `id` 一律得到「发票抬头不存在」，不可能看到或改到别人的抬头。
 *
 * 📌 下单时选的抬头**不经过这里**：它是下单请求体里的 `invoice` 快照（Q27/R27）。
 *
 * @author 芋道源码
 */
@Tag(name = "用户 App - 发票抬头")
@RestController
@RequestMapping("/trade/invoice-title")
@Validated
public class AppInvoiceTitleController {

    @Resource
    private TradeInvoiceTitleService tradeInvoiceTitleService;

    @GetMapping("/list")
    @Operation(summary = "获得发票抬头列表", description = "默认的那条排在最前")
    public CommonResult<List<AppInvoiceTitleRespVO>> getInvoiceTitleList() {
        List<TradeInvoiceTitleDO> list = tradeInvoiceTitleService.getInvoiceTitleList(getLoginUserId());
        return success(BeanUtils.toBean(list, AppInvoiceTitleRespVO.class));
    }

    @PostMapping("/create")
    @Operation(summary = "新增发票抬头")
    public CommonResult<Long> createInvoiceTitle(@Valid @RequestBody AppInvoiceTitleSaveReqVO reqVO) {
        return success(tradeInvoiceTitleService.createInvoiceTitle(getLoginUserId(), reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "修改发票抬头")
    public CommonResult<Boolean> updateInvoiceTitle(@Valid @RequestBody AppInvoiceTitleSaveReqVO reqVO) {
        tradeInvoiceTitleService.updateInvoiceTitle(getLoginUserId(), reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除发票抬头", description = "不影响任何历史订单的开票信息（申请里存的是快照）")
    @Parameter(name = "id", description = "抬头编号", required = true, example = "3")
    public CommonResult<Boolean> deleteInvoiceTitle(@RequestParam("id") Long id) {
        tradeInvoiceTitleService.deleteInvoiceTitle(getLoginUserId(), id);
        return success(true);
    }

}
