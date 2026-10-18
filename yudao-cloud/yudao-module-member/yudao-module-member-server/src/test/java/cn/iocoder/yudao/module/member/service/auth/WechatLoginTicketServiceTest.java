package cn.iocoder.yudao.module.member.service.auth;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.member.dal.redis.wechat.WechatLoginTicketRedisDAO;
import cn.iocoder.yudao.module.member.service.auth.bo.WechatLoginTicketBO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.WECHAT_LOGIN_TICKET_NOT_EXISTS;
import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.WECHAT_LOGIN_TICKET_STATUS_INVALID;
import static cn.iocoder.yudao.module.member.service.auth.WechatLoginTicketStatusEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 微信扫码登录的**票据状态机**测试（T342）
 *
 * 用**纯 Mockito**、不起 Spring 上下文 —— 本机没有跑 unit-test profile 要用的那个 Redis
 * （`127.0.0.1:16379`），起上下文只会得到一堆连接失败。票据的逻辑本来就是纯内存的。
 *
 * ⚠️ 假 Redis **存 JSON 字符串**、不存对象引用。真实 DAO 走的就是 JSON 序列化，
 * 若这里存引用，"service 改了对象却忘了写回"这种 bug 会被悄悄放过（测试假绿）。
 */
@DisplayName("微信扫码登录 —— 票据状态机（T342）")
class WechatLoginTicketServiceTest {

    private WechatLoginTicketRedisDAO dao;
    private WechatLoginTicketService service;

    /** 假 Redis：key 是拼好的字符串，value 是 JSON */
    private final Map<String, String> redis = new HashMap<>();

    @BeforeEach
    void setUp() {
        redis.clear();
        dao = mock(WechatLoginTicketRedisDAO.class);
        service = new WechatLoginTicketServiceImpl();
        ReflectionTestUtils.setField(service, "ticketRedisDAO", dao);

        when(dao.get(anyString())).thenAnswer(invocation ->
                JsonUtils.parseObject(redis.get("ticket:" + invocation.getArgument(0, String.class)),
                        WechatLoginTicketBO.class));
        doAnswer(invocation -> {
            WechatLoginTicketBO ticket = invocation.getArgument(0);
            redis.put("ticket:" + ticket.getTicket(), JsonUtils.toJsonString(ticket));
            return null;
        }).when(dao).set(any(), anyLong());
        doAnswer(invocation -> {
            redis.remove("ticket:" + invocation.getArgument(0, String.class));
            return null;
        }).when(dao).delete(anyString());

        when(dao.getTicketByState(anyString())).thenAnswer(invocation ->
                redis.get("state:" + invocation.getArgument(0, String.class)));
        doAnswer(invocation -> {
            redis.put("state:" + invocation.getArgument(0, String.class), invocation.getArgument(1, String.class));
            return null;
        }).when(dao).setTicketByState(anyString(), anyString(), anyLong());
        doAnswer(invocation -> {
            redis.remove("state:" + invocation.getArgument(0, String.class));
            return null;
        }).when(dao).deleteTicketByState(anyString());
    }

    // ========== 签发 ==========

    @Test
    @DisplayName("刚签发的票据是「待扫」，且**此刻还没有会员与令牌**（FR-150 的数据形态）")
    void create_startsAsWaitingScan_withoutMemberOrToken() {
        WechatLoginTicketBO ticket = service.create("s-fixed");

        assertNotNull(ticket.getTicket());
        assertNotNull(ticket.getState());
        assertEquals(WAITING_SCAN.getStatus(), ticket.getStatus());
        assertNull(ticket.getMemberId(), "提交成功之前不许有会员（FR-150）");
        assertNull(ticket.getAccessToken(), "提交成功之前不许有令牌（FR-150）");

        // 从"存储"里读回来，同样是待扫 —— 证明它真的被写进去了
        WechatLoginTicketBO loaded = service.get(ticket.getTicket());
        assertNotNull(loaded);
        assertEquals(WAITING_SCAN.getStatus(), loaded.getStatus());
    }

    @Test
    @DisplayName("**`state` 用调用方传进来的那个**（它必须是 justauth 生成的，见接口注释）")
    void create_usesGivenState() {
        WechatLoginTicketBO ticket = service.create("s-from-justauth");
        assertEquals("s-from-justauth", ticket.getState());
        // 而且它**不是**票据号 —— 两个仍是不同的值（state 会出现在二维码与回跳 URL 里）
        assertNotEquals(ticket.getTicket(), ticket.getState());
        // 按这个 state 要能找回票据
        assertEquals(ticket.getTicket(), service.getByState("s-from-justauth").getTicket());
    }

    @Test
    @DisplayName("票据号不可猜测：100 次签发互不相同、且都有足够长度（FR-149）")
    void create_ticketIsUnguessable() {
        List<String> tickets = IntStream.range(0, 100)
                .mapToObj(i -> service.create("s-fixed").getTicket())
                .collect(Collectors.toList());

        assertEquals(100, new HashSet<>(tickets).size(), "100 次签发出现了重复");
        tickets.forEach(t -> assertTrue(t.length() >= 32, "票据太短，不足以称为不可猜测：" + t));
    }

    @Test
    @DisplayName("票据一定带 TTL，不会变成永久数据")
    void create_alwaysSetsTtl() {
        service.create("s-fixed");

        ArgumentCaptor<Long> ttl = ArgumentCaptor.forClass(Long.class);
        verify(dao).set(any(), ttl.capture());
        assertTrue(ttl.getValue() > 0 && ttl.getValue() <= 600,
                "TTL 必须在 (0, 10min]，实际 " + ttl.getValue());
    }

    // ========== 授权完成 → 待补全 ==========

    @Test
    @DisplayName("授权完成后状态变「待补全」，**但依然没有会员与令牌**（FR-132 / FR-150 —— 本轮最关键的一条）")
    void markWaitingProfile_keepsMemberAndTokenNull() {
        WechatLoginTicketBO ticket = service.create("s-fixed");

        service.markWaitingProfile(ticket.getState(), "o-123", "张三", "https://avatar.png");

        WechatLoginTicketBO loaded = service.get(ticket.getTicket());
        assertEquals(WAITING_PROFILE.getStatus(), loaded.getStatus());
        assertEquals("o-123", loaded.getOpenid());
        assertEquals("张三", loaded.getWxNickname());
        assertEquals("https://avatar.png", loaded.getWxAvatar());
        assertNull(loaded.getMemberId(), "提交成功之前不许建会员（FR-150）");
        assertNull(loaded.getAccessToken(), "提交成功之前不许签发令牌（FR-150）");
    }

    @Test
    @DisplayName("按 `state` 能找回票据 —— 微信回调只带 state，不带 ticket")
    void getByState_findsTicket() {
        WechatLoginTicketBO ticket = service.create("s-fixed");
        service.markWaitingProfile(ticket.getState(), "o-1", "n", "a");

        WechatLoginTicketBO loaded = service.getByState(ticket.getState());
        assertNotNull(loaded);
        assertEquals(ticket.getTicket(), loaded.getTicket());
        assertEquals("o-1", loaded.getOpenid());
    }

    // ========== 提交补全 → 已完成 ==========

    @Test
    @DisplayName("提交成功之后才带上会员与令牌")
    void markCompleted_carriesMemberAndToken() {
        WechatLoginTicketBO ticket = service.create("s-fixed");
        service.markWaitingProfile(ticket.getState(), "o-1", "n", "a");

        service.markCompleted(ticket.getTicket(), 42L, "at-1", "rt-1");

        WechatLoginTicketBO loaded = service.get(ticket.getTicket());
        assertEquals(COMPLETED.getStatus(), loaded.getStatus());
        assertEquals(42L, loaded.getMemberId());
        assertEquals("at-1", loaded.getAccessToken());
        assertEquals("rt-1", loaded.getRefreshToken());
    }

    @Test
    @DisplayName("取走令牌后票据**立刻失效**（一次性，FR-149）—— 连 state 索引也一起清掉")
    void remove_makesTicketUnusable() {
        WechatLoginTicketBO ticket = service.create("s-fixed");
        service.markWaitingProfile(ticket.getState(), "o-1", "n", "a");
        service.markCompleted(ticket.getTicket(), 42L, "at-1", "rt-1");

        service.remove(ticket.getTicket());

        assertNull(service.get(ticket.getTicket()), "取走之后就不该再查得到（FR-149）");
        assertNull(service.getByState(ticket.getState()), "state 索引也要一起清掉，否则会留下一个指不到东西的键");
    }

    // ========== 作废与非法状态 ==========

    @Test
    @DisplayName("客户关掉补全页 → 「已作废」")
    void abandon_marksTicketAbandoned() {
        WechatLoginTicketBO ticket = service.create("s-fixed");
        service.markWaitingProfile(ticket.getState(), "o-1", "n", "a");

        service.abandon(ticket.getTicket());

        assertEquals(ABANDONED.getStatus(), service.get(ticket.getTicket()).getStatus());
    }

    @Test
    @DisplayName("作废是幂等的：对已不存在的票据调用**不报错**（客户关得晚了一点，不该看到一个错误）")
    void abandon_isIdempotent() {
        assertDoesNotThrow(() -> service.abandon("no-such-ticket"));
    }

    @Test
    @DisplayName("回调带了个对不上的 state → 明确报错，**不静默假装成功**（FR-152）")
    void markWaitingProfile_unknownState_throws() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.markWaitingProfile("no-such-state", "o-1", "n", "a"));
        assertEquals(WECHAT_LOGIN_TICKET_NOT_EXISTS.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("还没授权就直接提交 → 拒绝（不许跳过授权）")
    void markCompleted_beforeAuthorize_throws() {
        WechatLoginTicketBO ticket = service.create("s-fixed");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.markCompleted(ticket.getTicket(), 1L, "at", "rt"));
        assertEquals(WECHAT_LOGIN_TICKET_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("已作废的票据不能再被提交")
    void markCompleted_afterAbandon_throws() {
        WechatLoginTicketBO ticket = service.create("s-fixed");
        service.markWaitingProfile(ticket.getState(), "o-1", "n", "a");
        service.abandon(ticket.getTicket());

        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.markCompleted(ticket.getTicket(), 1L, "at", "rt"));
        assertEquals(WECHAT_LOGIN_TICKET_STATUS_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("对不存在的票据提交 → 报「已过期」而不是空指针")
    void markCompleted_unknownTicket_throws() {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.markCompleted("no-such-ticket", 1L, "at", "rt"));
        assertEquals(WECHAT_LOGIN_TICKET_NOT_EXISTS.getCode(), ex.getCode());
    }

}
