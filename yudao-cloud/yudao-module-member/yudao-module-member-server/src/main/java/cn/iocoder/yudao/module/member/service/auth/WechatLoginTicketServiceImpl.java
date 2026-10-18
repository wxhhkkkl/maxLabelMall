package cn.iocoder.yudao.module.member.service.auth;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.member.dal.redis.wechat.WechatLoginTicketRedisDAO;
import cn.iocoder.yudao.module.member.service.auth.bo.WechatLoginTicketBO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.Duration;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.WECHAT_LOGIN_TICKET_NOT_EXISTS;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.WECHAT_LOGIN_TICKET_STATUS_INVALID;
import static cn.iocoder.yudao.module.member.service.auth.WechatLoginTicketStatusEnum.*;

/**
 * 微信扫码登录的「登录票据」Service 实现
 *
 * 状态机见 {@link WechatLoginTicketService} 的接口注释。
 */
@Service
@Slf4j
public class WechatLoginTicketServiceImpl implements WechatLoginTicketService {

    /**
     * 票据存活时长。
     *
     * 📌 **5 分钟是规格自行补的默认值**（spec.md FR-152），**不是所有者表态过的数字** ——
     * 按微信授权码的实际寿命在实施时确认（research.md R18）。
     */
    private static final Duration TTL = Duration.ofMinutes(5);

    @Resource
    private WechatLoginTicketRedisDAO ticketRedisDAO;

    @Override
    public WechatLoginTicketBO create(String state) {
        // 票据号必须不可猜测（FR-149）—— 它在"待补全"期间等价于登录凭据。
        // 用 IdUtil.fastSimpleUUID() 是本仓库签发令牌的既有做法（OAuth2Code/Token 都这么干）。
        //
        // ⚠️ **state 不由我们生成**：它必须是 justauth 生成、并缓存起来的那个，
        //    否则回调时 justauth 会抛 `Illegal state [WECHAT_MP]`。见接口注释。
        WechatLoginTicketBO ticket = WechatLoginTicketBO.builder()
                .ticket(IdUtil.fastSimpleUUID())
                .state(state)
                .status(WAITING_SCAN.getStatus())
                .build();
        save(ticket);
        return ticket;
    }

    @Override
    public long getTtlSeconds() {
        return TTL.getSeconds();
    }

    @Override
    public WechatLoginTicketBO get(String ticket) {
        return StrUtil.isBlank(ticket) ? null : ticketRedisDAO.get(ticket);
    }

    @Override
    public WechatLoginTicketBO getByState(String state) {
        if (StrUtil.isBlank(state)) {
            return null;
        }
        return get(ticketRedisDAO.getTicketByState(state));
    }

    @Override
    public void markWaitingProfile(String state, String openid, String wxNickname, String wxAvatar) {
        WechatLoginTicketBO ticket = getByState(state);
        if (ticket == null) {
            throw exception(WECHAT_LOGIN_TICKET_NOT_EXISTS);
        }
        // 只有「待扫」能进这一步：重复回调、或已作废的票据都不许再改（不然会覆盖掉后面的状态）
        if (!WAITING_SCAN.getStatus().equals(ticket.getStatus())) {
            throw exception(WECHAT_LOGIN_TICKET_STATUS_INVALID);
        }
        // ⚠️ 到这里为止**只写身份**：MUST NOT 建会员、MUST NOT 建绑定、MUST NOT 签发令牌（FR-132 / FR-150）
        ticket.setStatus(WAITING_PROFILE.getStatus());
        ticket.setOpenid(openid);
        ticket.setWxNickname(wxNickname);
        ticket.setWxAvatar(wxAvatar);
        save(ticket);
    }

    @Override
    public void markCompleted(String ticketId, Long memberId, String accessToken, String refreshToken) {
        WechatLoginTicketBO ticket = get(ticketId);
        if (ticket == null) {
            throw exception(WECHAT_LOGIN_TICKET_NOT_EXISTS);
        }
        // 只有「待补全」能进这一步 —— 不许跳过授权，也不许对已作废的票据提交
        if (!WAITING_PROFILE.getStatus().equals(ticket.getStatus())) {
            throw exception(WECHAT_LOGIN_TICKET_STATUS_INVALID);
        }
        ticket.setStatus(COMPLETED.getStatus());
        ticket.setMemberId(memberId);
        ticket.setAccessToken(accessToken);
        ticket.setRefreshToken(refreshToken);
        save(ticket);
    }

    @Override
    public void abandon(String ticketId) {
        WechatLoginTicketBO ticket = get(ticketId);
        if (ticket == null) {
            // 幂等：票据本来就过期了，也算"已经作废"。
            // 客户关页面关得晚一点，不该看到一个错误（FR-148）
            return;
        }
        ticket.setStatus(ABANDONED.getStatus());
        save(ticket);
    }

    @Override
    public void remove(String ticketId) {
        WechatLoginTicketBO ticket = get(ticketId);
        if (ticket == null) {
            return;
        }
        // state 索引也要一起清掉：否则会留下一个指向不存在票据的键，
        // 直到它自己的 TTL 到期为止
        ticketRedisDAO.deleteTicketByState(ticket.getState());
        ticketRedisDAO.delete(ticketId);
    }

    private void save(WechatLoginTicketBO ticket) {
        long seconds = TTL.getSeconds();
        ticketRedisDAO.set(ticket, seconds);
        ticketRedisDAO.setTicketByState(ticket.getState(), ticket.getTicket(), seconds);
    }

}
