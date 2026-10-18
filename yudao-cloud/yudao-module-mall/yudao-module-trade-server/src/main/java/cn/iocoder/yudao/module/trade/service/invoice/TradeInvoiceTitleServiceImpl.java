package cn.iocoder.yudao.module.trade.service.invoice;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.trade.controller.app.invoice.vo.AppInvoiceTitleSaveReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.invoice.TradeInvoiceTitleDO;
import cn.iocoder.yudao.module.trade.dal.mysql.invoice.TradeInvoiceTitleMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.INVOICE_TITLE_NOT_FOUND;

/**
 * 会员的发票抬头簿 Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
public class TradeInvoiceTitleServiceImpl implements TradeInvoiceTitleService {

    @Resource
    private TradeInvoiceTitleMapper titleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createInvoiceTitle(Long userId, AppInvoiceTitleSaveReqVO reqVO) {
        // 校验（唯一一处规则）：**按抬头自己的票种**判必填 ——
        // 普票抬头只要名称，专票抬头五项全填（Q36）
        InvoiceValidator.validate(reqVO);

        TradeInvoiceTitleDO title = BeanUtils.toBean(reqVO, TradeInvoiceTitleDO.class);
        title.setId(null); // 防一手：请求里带了 id 也当新增
        title.setUserId(userId); // 归属一律从登录态取
        title.setDefaultStatus(Boolean.TRUE.equals(reqVO.getDefaultStatus()));
        if (Boolean.TRUE.equals(title.getDefaultStatus())) {
            titleMapper.clearDefaultByUserIdAndType(userId, title.getType());
        }
        titleMapper.insert(title);
        return title.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateInvoiceTitle(Long userId, AppInvoiceTitleSaveReqVO reqVO) {
        // 先确认这条抬头是**本人的** —— 不属于自己的一律"抬头不存在"（不泄露它是否存在）
        validateTitleExists(userId, reqVO.getId());
        InvoiceValidator.validate(reqVO);

        TradeInvoiceTitleDO update = BeanUtils.toBean(reqVO, TradeInvoiceTitleDO.class);
        update.setUserId(userId);
        update.setDefaultStatus(Boolean.TRUE.equals(reqVO.getDefaultStatus()));
        // 默认唯一：清旧 + 置新**同一事务**，否则中途失败会留下"一条默认都没有"
        if (Boolean.TRUE.equals(update.getDefaultStatus())) {
            titleMapper.clearDefaultByUserIdAndType(userId, update.getType());
        }
        titleMapper.updateById(update);
    }

    @Override
    public void deleteInvoiceTitle(Long userId, Long id) {
        validateTitleExists(userId, id);
        titleMapper.deleteById(id);
    }

    @Override
    public List<TradeInvoiceTitleDO> getInvoiceTitleList(Long userId) {
        return titleMapper.selectListByUserId(userId);
    }

    private void validateTitleExists(Long userId, Long id) {
        if (id == null || titleMapper.selectByIdAndUserId(id, userId) == null) {
            throw exception(INVOICE_TITLE_NOT_FOUND);
        }
    }

}
