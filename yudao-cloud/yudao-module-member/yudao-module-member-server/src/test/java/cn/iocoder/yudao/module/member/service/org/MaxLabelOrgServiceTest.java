package cn.iocoder.yudao.module.member.service.org;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * 企业主体：建档同时写 OWNER 行、启停、筛选、一人一企业。
 */
@Import(MaxLabelOrgServiceImpl.class)
public class MaxLabelOrgServiceTest extends BaseDbUnitTest {

    @Resource
    private MaxLabelOrgService orgService;
    @Resource
    private MaxLabelOrgMapper orgMapper;
    @Resource
    private MaxLabelOrgMemberMapper orgMemberMapper;
    @Resource
    private MemberUserMapper userMapper;

    private Long ownerId;

    @BeforeEach
    void setUp() {
        MemberUserDO owner = MemberUserDO.builder()
                .mobile("13800138020").nickname("企业主").status(0).registerIp("127.0.0.1").build();
        userMapper.insert(owner);
        ownerId = owner.getId();
    }

    private Long addUser(String mobile) {
        MemberUserDO user = MemberUserDO.builder()
                .mobile(mobile).nickname("会员" + mobile).status(0).registerIp("127.0.0.1").build();
        userMapper.insert(user);
        return user.getId();
    }

    @Test
    void createOrgWritesOwnerRow() {
        Long orgId = orgService.createOrg("测试企业", ownerId, "");

        MaxLabelOrgDO org = orgMapper.selectById(orgId);
        assertEquals(ownerId, org.getOwnerMemberId());
        MaxLabelOrgMemberDO relation = orgMemberMapper.selectByMemberId(ownerId);
        assertEquals(orgId, relation.getOrgId());
        assertEquals(MaxLabelOrgRoleEnum.OWNER.getRole(), relation.getRole());
    }

    @Test
    void disableBlocksAdminAndKeepsMemberVisible() {
        Long orgId = orgService.createOrg("测试企业", ownerId, "");
        orgService.updateStatus(orgId, 1);

        assertThrows(Exception.class, () -> orgService.requireOrgAdmin(ownerId));
        assertNotNull(orgService.getMemberOrg(ownerId));
        assertEquals(1, orgService.getMemberOrg(ownerId).getStatus());
    }

    @Test
    void pageFiltersByName() {
        orgService.createOrg("甲企业", ownerId, "");
        Long otherOwner = addUser("13800138021");
        orgService.createOrg("乙企业", otherOwner, "");

        var page = orgService.getOrgPage("甲", null, new PageParam());
        assertEquals(1L, page.getTotal());
        assertEquals("甲企业", page.getList().get(0).getName());
    }

    @Test
    void memberAlreadyInOtherOrgCannotCreateAgain() {
        orgService.createOrg("甲企业", ownerId, "");
        assertThrows(Exception.class, () -> orgService.createOrg("乙企业", ownerId, ""));
    }

}
