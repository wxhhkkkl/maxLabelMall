package cn.iocoder.yudao.module.member.service.org;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgDO;
import cn.iocoder.yudao.module.member.dal.dataobject.org.MaxLabelOrgMemberDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgMapper;
import cn.iocoder.yudao.module.member.dal.mysql.org.MaxLabelOrgMemberMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.enums.org.MaxLabelOrgRoleEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import static cn.iocoder.yudao.module.member.enums.ErrorCodeConstants.MEMBER_ORG_MEMBER_IN_OTHER;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 企业成员：OWNER 唯一、按手机号加入、转交、退出、一人一企业。
 */
@Import({MaxLabelOrgServiceImpl.class, MaxLabelOrgMemberServiceImpl.class})
public class MaxLabelOrgMemberServiceImplTest extends BaseDbUnitTest {

    private static final int OPERATOR_MEMBER = 1;

    @Resource
    private MaxLabelOrgService orgService;
    @Resource
    private MaxLabelOrgMemberService memberService;
    @Resource
    private MaxLabelOrgMapper orgMapper;
    @Resource
    private MaxLabelOrgMemberMapper orgMemberMapper;
    @Resource
    private MemberUserMapper userMapper;

    private Long orgId;
    private Long ownerId;

    @BeforeEach
    void setUp() {
        MemberUserDO owner = MemberUserDO.builder()
                .mobile("13800138011").nickname("企业主").status(0).registerIp("127.0.0.1").build();
        userMapper.insert(owner);
        ownerId = owner.getId();
        orgId = orgService.createOrg("成员测试企业", ownerId, "");
    }

    private Long addUser(String mobile) {
        MemberUserDO user = MemberUserDO.builder()
                .mobile(mobile).nickname("成员" + mobile).status(0).registerIp("127.0.0.1").build();
        userMapper.insert(user);
        return user.getId();
    }

    @Test
    void createOrgCreatesOwnerRow() {
        MaxLabelOrgMemberDO relation = orgMemberMapper.selectByMemberId(ownerId);
        assertNotNull(relation);
        assertEquals(orgId, relation.getOrgId());
        assertEquals(MaxLabelOrgRoleEnum.OWNER.getRole(), relation.getRole());
    }

    @Test
    void addByMobileAndUnknownMobileRejected() {
        Long memberId = addUser("13800138012");
        memberService.addMember(orgId, "13800138012", OPERATOR_MEMBER, ownerId);
        assertNotNull(orgMemberMapper.selectByOrgIdAndMemberId(orgId, memberId));

        assertThrows(Exception.class,
                () -> memberService.addMember(orgId, "13900000000", OPERATOR_MEMBER, ownerId));
    }

    @Test
    void removeOwnerRejected() {
        assertThrows(Exception.class,
                () -> memberService.removeMember(orgId, ownerId, OPERATOR_MEMBER, ownerId));
    }

    @Test
    void transferOwnerSwapsRolesAndUpdatesOrg() {
        Long memberId = addUser("13800138013");
        memberService.addMember(orgId, "13800138013", OPERATOR_MEMBER, ownerId);
        memberService.updateRole(orgId, memberId, MaxLabelOrgRoleEnum.ADMIN.getRole(),
                OPERATOR_MEMBER, ownerId);

        memberService.transferOwner(orgId, memberId, OPERATOR_MEMBER, ownerId);

        MaxLabelOrgDO org = orgMapper.selectById(orgId);
        assertEquals(memberId, org.getOwnerMemberId());
        assertEquals(MaxLabelOrgRoleEnum.OWNER.getRole(),
                orgMemberMapper.selectByOrgIdAndMemberId(orgId, memberId).getRole());
        assertEquals(MaxLabelOrgRoleEnum.ADMIN.getRole(),
                orgMemberMapper.selectByOrgIdAndMemberId(orgId, ownerId).getRole());
    }

    @Test
    void ownerCannotLeaveButMemberCan() {
        assertThrows(Exception.class, () -> memberService.leave(ownerId));

        Long memberId = addUser("13800138014");
        memberService.addMember(orgId, "13800138014", OPERATOR_MEMBER, ownerId);
        memberService.leave(memberId);
        assertNull(orgMemberMapper.selectByMemberId(memberId));
    }

    @Test
    void oneOrgPerMember() {
        Long memberId = addUser("13800138015");
        memberService.addMember(orgId, "13800138015", OPERATOR_MEMBER, ownerId);

        // 另起一个企业，仍由 owner 建（owner 已在原企业，应该被拒）
        assertThrows(Exception.class, () -> orgService.createOrg("另一个企业", ownerId, ""));
    }

    @Test
    void joinSecondOrgIsRejectedWithBusinessError() {
        addUser("13800138016");
        memberService.addMember(orgId, "13800138016", OPERATOR_MEMBER, ownerId);

        Long otherOwnerId = addUser("13800138017");
        Long otherOrgId = orgService.createOrg("第二家企业", otherOwnerId, "");

        // 已在其他企业 → 必须是业务错误，而不是数据库唯一键异常（FR-290）
        ServiceException exception = assertThrows(ServiceException.class,
                () -> memberService.addMember(otherOrgId, "13800138016", OPERATOR_MEMBER, otherOwnerId));
        assertEquals(MEMBER_ORG_MEMBER_IN_OTHER.getCode(), exception.getCode());
    }

}
