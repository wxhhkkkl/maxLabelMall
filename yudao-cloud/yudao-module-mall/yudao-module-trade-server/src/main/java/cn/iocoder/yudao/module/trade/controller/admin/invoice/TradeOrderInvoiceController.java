package cn.iocoder.yudao.module.trade.controller.admin.invoice;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.trade.controller.admin.invoice.vo.TradeOrderInvoicePageReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.invoice.vo.TradeOrderInvoiceRespVO;
import cn.iocoder.yudao.module.trade.service.invoice.TradeOrderInvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 管理后台 - 开票申请
 *
 * ⚠️ **两个接口都必须挂权限点**（Q29 / FR-174）：「上传发票 = 把订单标记成已开票」是**有财务效力**的动作，
 * MUST NOT 只靠前端藏按钮。而权限点又**必须登记进 `system_menu`**，否则连超管都看不到按钮
 * （004 的踩坑记录：接口能调、按钮不显示）——见 `sql/mysql/008-order-invoice-menus.sql`。
 *
 * @author 芋道源码
 */
@Tag(name = "管理后台 - 开票申请")
@RestController
@RequestMapping("/trade/order-invoice")
@Validated
public class TradeOrderInvoiceController {

    @Resource
    private TradeOrderInvoiceService tradeOrderInvoiceService;
    @Resource
    private MemberUserApi memberUserApi;

    @GetMapping("/page")
    @Operation(summary = "获得开票申请分页", description = "待开票列表只含已支付订单的申请")
    @PreAuthorize("@ss.hasPermission('trade:order-invoice:query')")
    public CommonResult<PageResult<TradeOrderInvoiceRespVO>> getInvoicePage(@Valid TradeOrderInvoicePageReqVO pageReqVO) {
        PageResult<TradeOrderInvoiceRespVO> pageResult = tradeOrderInvoiceService.getInvoicePage(pageReqVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty());
        }

        // 回填会员昵称（沿用 AfterSaleController 的既有做法：一次批量取，不逐条查）
        Map<Long, MemberUserRespDTO> userMap = memberUserApi.getUserMap(
                convertSet(pageResult.getList(), TradeOrderInvoiceRespVO::getUserId));
        pageResult.getList().forEach(invoice -> {
            MemberUserRespDTO user = userMap.get(invoice.getUserId());
            invoice.setUserNickname(user != null ? user.getNickname() : null);
        });
        return success(pageResult);
    }

    @PutMapping("/issue")
    @Operation(summary = "开具发票", description = "把已开好的发票文件挂到申请上；重新上传即覆盖（只留最新一份）")
    @Parameter(name = "id", description = "开票申请编号", required = true, example = "77")
    @Parameter(name = "fileUrl", description = "发票文件地址（先传到 infra 文件服务）", required = true)
    @PreAuthorize("@ss.hasPermission('trade:order-invoice:issue')")
    public CommonResult<Boolean> issueInvoice(@RequestParam("id") Long id,
                                             @RequestParam("fileUrl") String fileUrl) {
        tradeOrderInvoiceService.issueInvoice(id, fileUrl, getLoginUserId());
        return success(true);
    }

}
