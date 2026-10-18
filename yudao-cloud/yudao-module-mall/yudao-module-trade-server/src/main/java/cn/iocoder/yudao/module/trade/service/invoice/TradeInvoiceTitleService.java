package cn.iocoder.yudao.module.trade.service.invoice;

import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceTitleSaveReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.invoice.TradeInvoiceTitleDO;

import java.util.List;

/**
 * 会员的发票抬头簿 Service（US23 / FR-164~166）
 *
 * ⚠️ **所有读写都必须带 `userId`**：它既是查询条件，也是"这条抬头是不是本人的"的判据 ——
 * 传别人的 id 一律得到「发票抬头不存在」，不能改到别人的抬头。
 *
 * ⚠️ 抬头簿与**开票申请**之间的关系是：申请里存的是**快照**，不是对抬头簿的引用。
 * 所以改 / 删抬头**结构上不可能**影响历史申请（FR-166 / SC-061）。
 *
 * @author 芋道源码
 */
public interface TradeInvoiceTitleService {

    /**
     * 新增一条抬头
     *
     * @param userId 当前登录会员
     * @return 新抬头编号
     */
    Long createInvoiceTitle(Long userId, AppInvoiceTitleSaveReqVO reqVO);

    /**
     * 修改一条抬头（只能是本人的）
     *
     * @param reqVO 必须带 `id`
     */
    void updateInvoiceTitle(Long userId, AppInvoiceTitleSaveReqVO reqVO);

    /**
     * 删除一条抬头（只能是本人的）
     *
     * ⚠️ 删除**不影响**任何历史申请（它们存的是快照）。
     */
    void deleteInvoiceTitle(Long userId, Long id);

    /** 某会员的全部抬头，**默认的那条排在最前**（结算页要拿它做预选） */
    List<TradeInvoiceTitleDO> getInvoiceTitleList(Long userId);

}
