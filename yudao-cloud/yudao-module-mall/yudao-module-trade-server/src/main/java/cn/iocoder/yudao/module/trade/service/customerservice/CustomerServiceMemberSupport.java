package cn.iocoder.yudao.module.trade.service.customerservice;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.CUSTOMER_SERVICE_MEMBER_NOT_EXISTS;

/**
 * 客户服务 - 会员检索与校验（021 / R6）
 *
 * 代建时先按手机号精准匹配，否则按昵称模糊匹配；不直连会员表。
 */
@Component
public class CustomerServiceMemberSupport {

    @Resource
    private MemberUserApi memberUserApi;

    /** 校验会员存在；不存在时抛出明确错误（FR-582） */
    public MemberUserRespDTO validateMember(Long memberId) {
        if (memberId == null) {
            throw exception(CUSTOMER_SERVICE_MEMBER_NOT_EXISTS);
        }
        MemberUserRespDTO user = memberUserApi.getUser(memberId).getCheckedData();
        if (user == null) {
            throw exception(CUSTOMER_SERVICE_MEMBER_NOT_EXISTS);
        }
        return user;
    }

    /** 按手机号（11 位）精准 / 昵称模糊检索，最多返回 20 条 */
    public List<MemberUserRespDTO> search(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return Collections.emptyList();
        }
        String value = keyword.trim();
        if (isMobile(value)) {
            MemberUserRespDTO user = memberUserApi.getUserByMobile(value).getCheckedData();
            return user == null ? Collections.emptyList() : List.of(user);
        }
        List<MemberUserRespDTO> list = memberUserApi.getUserListByNickname(value).getCheckedData();
        if (list == null) {
            return Collections.emptyList();
        }
        return list.size() > 20 ? list.subList(0, 20) : list;
    }

    static boolean isMobile(String value) {
        return value != null && value.matches("^1\\d{10}$");
    }

}
