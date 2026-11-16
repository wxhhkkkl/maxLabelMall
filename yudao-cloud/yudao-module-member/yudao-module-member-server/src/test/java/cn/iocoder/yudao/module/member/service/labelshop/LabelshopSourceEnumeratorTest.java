package cn.iocoder.yudao.module.member.service.labelshop;

import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopCate;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopCompany;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopEnumerationResult;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopSession;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopSourceItem;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopSts;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LabelshopSourceEnumeratorTest {

    @Mock
    private LabelshopClient client;

    @Test
    void enumerate_mapsWhitelistedLibrariesOnly() {
        LabelshopSession session = new LabelshopSession();
        session.setUid(26L);
        session.setMid("mid");
        session.setToken("token");
        session.setCompanies(List.of(new LabelshopCompany(103L, "云马科技")));

        when(client.getCateTree(session, 0, 1))
                .thenReturn(List.of(new LabelshopCate(1L, 0L, "我的模板")));
        when(client.getCateTree(session, 12, 4))
                .thenReturn(List.of(new LabelshopCate(10L, 0L, "电信电力")));
        when(client.getCateList(session, 1L, 0)).thenReturn(List.of());
        when(client.getCateList(session, 1L, 12))
                .thenReturn(List.of(new LabelshopTemplate("a", "模板A")));
        when(client.getCateList(session, 10L, 12))
                .thenReturn(List.of(new LabelshopTemplate("b", "模板B")));
        LabelshopSts sts = new LabelshopSts();
        sts.setBucketTmpl("cvstmpl");
        when(client.getSts(session)).thenReturn(sts);
        when(client.getCateList(session, 0L, 1, 103L))
                .thenReturn(List.of(new LabelshopTemplate("c", "模板C")));

        LabelshopEnumerationResult result = new LabelshopSourceEnumerator(client)
                .enumerate(session, 88L, 1001L);

        assertEquals(3, result.getItems().size());
        LabelshopSourceItem mine = find(result, "a");
        assertEquals(12, mine.getSourceLibrary());
        assertEquals(1, mine.getTargetScope());
        assertEquals(1001L, mine.getTargetOwnerId());
        assertEquals("我的模板", mine.getSourceCategory());
        assertEquals("lsdx", mine.getFormat());
        assertEquals(12, mine.getOwnType());

        LabelshopSourceItem group = find(result, "b");
        assertEquals(13, group.getSourceLibrary());
        assertEquals(2, group.getTargetScope());
        assertEquals(88L, group.getTargetOwnerId());
        assertEquals(12, group.getOwnType());

        LabelshopSourceItem company = find(result, "c");
        assertEquals(1, company.getSourceLibrary());
        assertEquals(2, company.getTargetScope());
        assertEquals(88L, company.getTargetOwnerId());
        assertEquals("企业/组织模板/云马科技", company.getSourceCategory());
        assertEquals("cloud-json", company.getFormat());
        assertEquals(1, company.getOwnType());
        assertSame(sts, result.getSts());

        // 公共模板库（树 TYPE=14 / 列表 Own_Type=14）与其他类型不读取
        verify(client, never()).getCateTree(session, 14, 1);
        verify(client, never()).getCateList(any(), anyLong(), eq(14));
        verify(client, never()).getCateList(any(), anyLong(), eq(14), anyLong());
    }

    @Test
    void enumeratePersonal_readsMineOnlyAndTargetsMember() {
        LabelshopSession session = new LabelshopSession();
        session.setUid(26L);
        session.setMid("mid");
        session.setToken("token");
        session.setCompanies(List.of(new LabelshopCompany(103L, "云马科技")));

        when(client.getCateTree(session, 0, 1))
                .thenReturn(List.of(new LabelshopCate(1L, 0L, "我的模板")));
        when(client.getCateList(session, 1L, 0)).thenReturn(List.of());
        when(client.getCateList(session, 1L, 12))
                .thenReturn(List.of(new LabelshopTemplate("a", "模板A")));

        LabelshopEnumerationResult result = new LabelshopSourceEnumerator(client)
                .enumeratePersonal(session, 1001L);

        assertEquals(1, result.getItems().size());
        LabelshopSourceItem mine = find(result, "a");
        assertEquals(12, mine.getSourceLibrary());
        assertEquals(1, mine.getTargetScope());
        assertEquals(1001L, mine.getTargetOwnerId());
        assertEquals(12, mine.getOwnType());
        // 个人代导不读取组模板库与企业/组织模板
        verify(client, never()).getCateTree(session, 12, 4);
        verify(client, never()).getCateList(session, 0L, 1, 103L);
        verify(client, never()).getSts(any());
    }

    @Test
    void enumeratePersonal_fallsBackToOldAccountOwnTypeEleven() {
        LabelshopSession session = new LabelshopSession();
        session.setUid(26L);
        when(client.getCateTree(session, 0, 1))
                .thenReturn(List.of(new LabelshopCate(1L, 0L, "我的模板")));
        when(client.getCateList(session, 1L, 0)).thenReturn(List.of());
        when(client.getCateList(session, 1L, 12)).thenReturn(List.of());
        when(client.getCateList(session, 1L, 11))
                .thenReturn(List.of(new LabelshopTemplate("old", "老账号模板")));

        LabelshopEnumerationResult result = new LabelshopSourceEnumerator(client)
                .enumeratePersonal(session, 1001L);

        LabelshopSourceItem item = find(result, "old");
        assertEquals(11, item.getOwnType());
        assertEquals("lsdx", item.getFormat());
    }

    @Test
    void enumeratePersonal_readsCloudJsonForOwnTypeZeroAndLoadsSts() {
        LabelshopSession session = new LabelshopSession();
        session.setUid(26L);
        when(client.getCateTree(session, 0, 1))
                .thenReturn(List.of(new LabelshopCate(1L, 0L, "我的模板")));
        when(client.getCateList(session, 1L, 0))
                .thenReturn(List.of(new LabelshopTemplate("cloud", "云模板")));
        LabelshopSts sts = new LabelshopSts();
        sts.setBucketTmpl("cvstmpl");
        when(client.getSts(session)).thenReturn(sts);

        LabelshopEnumerationResult result = new LabelshopSourceEnumerator(client)
                .enumeratePersonal(session, 1001L);

        LabelshopSourceItem item = find(result, "cloud");
        assertEquals(0, item.getOwnType());
        assertEquals("cloud-json", item.getFormat());
        assertSame(sts, result.getSts());
        verify(client).getSts(session);
    }

    @Test
    void enumeratePersonal_includesCateZeroAndLabelShop5AndNarrowsOwnType() {
        LabelshopSession session = new LabelshopSession();
        session.setUid(26L);
        when(client.getCateTree(session, 0, 1)).thenReturn(List.of(
                new LabelshopCate(0L, 0L, "CATE0", null),
                new LabelshopCate(-1L, 0L, "LabelShop5", 11)));
        when(client.getCateList(session, 0L, 0))
                .thenReturn(List.of(new LabelshopTemplate("c0", "CATE0 模板")));
        when(client.getCateList(session, -1L, 11))
                .thenReturn(List.of(new LabelshopTemplate("old", "老账号模板")));
        LabelshopSts sts = new LabelshopSts();
        sts.setBucketTmpl("cvstmpl");
        when(client.getSts(session)).thenReturn(sts);

        LabelshopEnumerationResult result = new LabelshopSourceEnumerator(client)
                .enumeratePersonal(session, 1001L);

        LabelshopSourceItem cate0 = find(result, "c0");
        assertEquals(0, cate0.getOwnType());
        assertEquals("cloud-json", cate0.getFormat());
        assertEquals("我的模板", cate0.getSourceCategory());

        LabelshopSourceItem old = find(result, "old");
        assertEquals(11, old.getOwnType());
        assertEquals("lsdx", old.getFormat());
        assertEquals("LabelShop5", old.getSourceCategory());
        assertSame(sts, result.getSts());

        // LabelShop5 分类只取 11，不应读取 0/12
        verify(client, never()).getCateList(session, -1L, 0);
        verify(client, never()).getCateList(session, -1L, 12);
    }

    private static LabelshopSourceItem find(LabelshopEnumerationResult result, String ref) {
        LabelshopSourceItem item = result.getItems().stream()
                .filter(value -> ref.equals(value.getSourceRef()))
                .findFirst().orElse(null);
        assertNotNull(item);
        return item;
    }

}
