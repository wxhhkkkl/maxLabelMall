package cn.iocoder.yudao.module.member.dal.redis.wechat;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.member.service.auth.bo.WechatLoginTicketBO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.module.member.dal.redis.RedisKeyConstants.WECHAT_LOGIN_STATE;
import static cn.iocoder.yudao.module.member.dal.redis.RedisKeyConstants.WECHAT_LOGIN_TICKET;

/**
 * {@link WechatLoginTicketBO} 的 RedisDAO
 *
 * 照 system 模块的 `OAuth2AccessTokenRedisDAO` 的写法（本仓库的既有范式）。
 *
 * ⚠️ **票据的一切都必须走 TTL** —— 它是临时凭据，绝不能变成永久数据。
 * 所以 `set` 的两个重载都要求传 TTL，且 MUST NOT 提供"不带 TTL"的写入方式。
 */
@Repository
public class WechatLoginTicketRedisDAO {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public WechatLoginTicketBO get(String ticket) {
        return JsonUtils.parseObject(
                stringRedisTemplate.opsForValue().get(formatTicketKey(ticket)),
                WechatLoginTicketBO.class);
    }

    public void set(WechatLoginTicketBO ticket, long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(formatTicketKey(ticket.getTicket()),
                JsonUtils.toJsonString(ticket), ttlSeconds, TimeUnit.SECONDS);
    }

    public void delete(String ticket) {
        stringRedisTemplate.delete(formatTicketKey(ticket));
    }

    /**
     * 按 `state` 找出它对应的票据号；没有则返回 null
     */
    public String getTicketByState(String state) {
        return stringRedisTemplate.opsForValue().get(formatStateKey(state));
    }

    public void setTicketByState(String state, String ticket, long ttlSeconds) {
        stringRedisTemplate.opsForValue().set(formatStateKey(state), ticket, ttlSeconds, TimeUnit.SECONDS);
    }

    public void deleteTicketByState(String state) {
        stringRedisTemplate.delete(formatStateKey(state));
    }

    private static String formatTicketKey(String ticket) {
        return String.format(WECHAT_LOGIN_TICKET, ticket);
    }

    private static String formatStateKey(String state) {
        return String.format(WECHAT_LOGIN_STATE, state);
    }

}
