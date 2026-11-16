package cn.iocoder.yudao.module.member.service.labelshop;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopCate;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopCompany;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopEnumerationResult;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopSession;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopSourceItem;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopSts;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按来源白名单枚举待导入模板。
 *
 * <p>树参数以完整版导出脚本为准（它是实际验证过客户端行为的版本）：</p>
 * <ul>
 *   <li>我的模板库：{@code GetCateTree(@TYPE=0, @MODE=1)}，列表 / 下载按
 *       {@code @OWNTYPE=0 → 12 → 11} 取第一个非空（0 = 云模板 JSON，11/12 = .lsdx）</li>
 *   <li>组模板库：{@code GetCateTree(@TYPE=12, @MODE=4)}，列表 / 下载按
 *       {@code @OWNTYPE=12 → 11} 取第一个非空</li>
 *   <li>企业 / 组织模板：{@code GetCateList(@CATEID=0, @OWNTYPE=1, @UID=公司 ID)} + OSS</li>
 * </ul>
 * <p>公共模板库（树 TYPE=14）与其他类型一律不读取。</p>
 */
@Component
public class LabelshopSourceEnumerator {

    private static final int LIB_MINE = 12;
    private static final int LIB_GROUP = 13;
    private static final int LIB_COMPANY = 1;

    /** 我的模板库：老账号 / 新账号 / 云模板的 Own_Type 候选顺序 */
    private static final int[] MINE_OWN_CANDIDATES = {0, 12, 11};
    /** 组模板库：新账号为 12，老账号（LabelShop5 迁移）为 11 */
    private static final int[] GROUP_OWN_CANDIDATES = {12, 11};

    public static final int SCOPE_PERSONAL = 1;
    public static final int SCOPE_ENTERPRISE = 2;

    private final LabelshopClient client;

    public LabelshopSourceEnumerator(LabelshopClient client) {
        this.client = client;
    }

    public LabelshopEnumerationResult enumerate(LabelshopSession session, Long orgId, Long ownerMemberId) {
        LabelshopEnumerationResult result = new LabelshopEnumerationResult();
        Set<String> seenRefs = new HashSet<>();
        enumerateMine(session, ownerMemberId, result, seenRefs);
        enumerateGroup(session, orgId, result, seenRefs);
        enumerateEnterprise(session, orgId, result, seenRefs);
        return result;
    }

    /**
     * 个人会员代导：只读取「我的模板库」，全部写入该会员的个人空间。
     */
    public LabelshopEnumerationResult enumeratePersonal(LabelshopSession session, Long memberId) {
        LabelshopEnumerationResult result = new LabelshopEnumerationResult();
        enumerateMine(session, memberId, result, new HashSet<>());
        return result;
    }

    private void enumerateMine(LabelshopSession session, Long ownerMemberId,
                               LabelshopEnumerationResult result, Set<String> seenRefs) {
        enumerateLibrary(session, LIB_MINE, 0, 1, MINE_OWN_CANDIDATES, true, "我的模板库",
                SCOPE_PERSONAL, ownerMemberId, result, seenRefs);
    }

    private void enumerateGroup(LabelshopSession session, Long orgId,
                                LabelshopEnumerationResult result, Set<String> seenRefs) {
        enumerateLibrary(session, LIB_GROUP, 12, 4, GROUP_OWN_CANDIDATES, false, "组模板库",
                SCOPE_ENTERPRISE, orgId, result, seenRefs);
    }

    private void enumerateLibrary(LabelshopSession session, int sourceLibrary,
                                  int treeType, int treeMode, int[] ownCandidates,
                                  boolean narrowByCategoryOwnType, String libraryName,
                                  int targetScope, Long targetOwnerId,
                                  LabelshopEnumerationResult result, Set<String> seenRefs) {
        List<LabelshopCate> tree;
        try {
            tree = client.getCateTree(session, treeType, treeMode);
        } catch (LabelshopSessionExpiredException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            result.getErrors().add(libraryName + "分类树读取失败：" + ex.getMessage());
            return;
        }
        Map<Long, LabelshopCate> index = new HashMap<>();
        for (LabelshopCate cate : tree) {
            index.put(cate.getCateId(), cate);
        }
        for (LabelshopCate cate : tree) {
            int ownType = -1;
            List<LabelshopTemplate> items = null;
            RuntimeException lastError = null;
            int[] candidates = narrowByCategoryOwnType ? candidatesFor(cate) : ownCandidates;
            for (int candidate : candidates) {
                try {
                    List<LabelshopTemplate> fetched = client.getCateList(session, cate.getCateId(), candidate);
                    if (fetched != null && !fetched.isEmpty()) {
                        ownType = candidate;
                        items = fetched;
                        break;
                    }
                } catch (LabelshopSessionExpiredException ex) {
                    throw ex;
                } catch (RuntimeException ex) {
                    lastError = ex;
                }
            }
            if (items == null || items.isEmpty()) {
                if (lastError != null) {
                    result.getErrors().add(libraryName + "分类[" + cate.getName() + "]列表读取失败："
                            + lastError.getMessage());
                }
                continue;
            }
            if (ownType == 0) {
                // 云模板正文在 OSS 上，和企业的私有 JSON 一样需要 STS
                ensureSts(session, result);
            }
            String category = categoryPath(index, cate);
            if (narrowByCategoryOwnType && "CATE0".equalsIgnoreCase(cate.getName())) {
                category = "我的模板";
            }
            addItems(items, sourceLibrary, category,
                    ownType == 0 ? "cloud-json" : "lsdx", ownType,
                    targetScope, targetOwnerId, result, seenRefs);
        }
    }

    /** 我的模板库：按分类自身的 Own_Type 收窄候选值（LabelShop5 分类只取 11） */
    private static int[] candidatesFor(LabelshopCate cate) {
        Integer ownType = cate.getOwnType();
        if (ownType == null || ownType == 0) {
            return MINE_OWN_CANDIDATES;
        }
        if (ownType == 11) {
            return new int[]{11};
        }
        return GROUP_OWN_CANDIDATES;
    }

    private void ensureSts(LabelshopSession session, LabelshopEnumerationResult result) {
        if (result.getSts() != null) {
            return;
        }
        try {
            result.setSts(client.getSts(session));
        } catch (LabelshopSessionExpiredException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            result.getErrors().add("云模板 STS 读取失败：" + ex.getMessage());
        }
    }

    private void enumerateEnterprise(LabelshopSession session, Long orgId,
                                     LabelshopEnumerationResult result, Set<String> seenRefs) {
        List<LabelshopCompany> companies = session.getCompanies();
        if (companies == null || companies.isEmpty()) {
            return;
        }
        LabelshopSts sts;
        try {
            sts = client.getSts(session);
        } catch (LabelshopSessionExpiredException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            result.getErrors().add("企业/组织模板 STS 读取失败：" + ex.getMessage());
            return;
        }
        result.setSts(sts);
        for (LabelshopCompany company : companies) {
            List<LabelshopTemplate> items;
            try {
                items = client.getCateList(session, 0, LIB_COMPANY, company.getId());
            } catch (LabelshopSessionExpiredException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                result.getErrors().add("企业/组织模板[" + company.getName() + "]列表读取失败："
                        + ex.getMessage());
                continue;
            }
            addItems(items, LIB_COMPANY, "企业/组织模板/" + company.getName(), "cloud-json", LIB_COMPANY,
                    SCOPE_ENTERPRISE, orgId, result, seenRefs);
        }
    }

    private void addItems(List<LabelshopTemplate> items, int sourceLibrary, String category,
                          String format, int ownType, int targetScope, Long targetOwnerId,
                          LabelshopEnumerationResult result, Set<String> seenRefs) {
        if (items == null) {
            return;
        }
        for (LabelshopTemplate item : items) {
            if (StrUtil.isBlank(item.getRef()) || !seenRefs.add(item.getRef())) {
                continue;
            }
            result.getItems().add(LabelshopSourceItem.builder()
                    .sourceLibrary(sourceLibrary)
                    .sourceRef(item.getRef())
                    .sourceCategory(StrUtil.blankToDefault(category, "未分类"))
                    .sourceName(StrUtil.nullToEmpty(item.getName()))
                    .format(format)
                    .ownType(ownType)
                    .targetScope(targetScope)
                    .targetOwnerId(targetOwnerId)
                    .build());
        }
    }

    private String categoryPath(Map<Long, LabelshopCate> index, LabelshopCate current) {
        List<String> chain = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        LabelshopCate cursor = current;
        while (cursor != null && cursor.getCateId() != null && seen.add(cursor.getCateId())) {
            if (StrUtil.isNotBlank(cursor.getName())) {
                chain.add(0, cursor.getName());
            }
            Long parentId = cursor.getParentId();
            if (parentId == null || parentId == 0) {
                break;
            }
            cursor = index.get(parentId);
        }
        return chain.isEmpty() ? "未分类" : String.join("/", chain);
    }

}
