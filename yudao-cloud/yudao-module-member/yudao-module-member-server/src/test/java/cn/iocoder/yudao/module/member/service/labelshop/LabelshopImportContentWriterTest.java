package cn.iocoder.yudao.module.member.service.labelshop;

import cn.iocoder.yudao.module.member.dal.dataobject.labelshop.LabelshopImportItemDO;
import cn.iocoder.yudao.module.member.dal.dataobject.share.MaxLabelShareContentDO;
import cn.iocoder.yudao.module.member.dal.mysql.labelshop.LabelshopImportItemMapper;
import cn.iocoder.yudao.module.member.dal.mysql.share.MaxLabelShareContentMapper;
import cn.iocoder.yudao.module.member.service.labelshop.dto.LabelshopSourceItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class LabelshopImportContentWriterTest {

    @Mock private MaxLabelShareContentMapper contentMapper;
    @Mock private LabelshopImportItemMapper itemMapper;
    @InjectMocks private LabelshopImportContentWriter writer;

    @Test
    void normalizeName_stripsExistingExtension() {
        assertEquals("面单.lsdx", LabelshopImportContentWriter.normalizeName("面单.lsdx", "uuid", ".lsdx"));
        assertEquals("面单.lsdx", LabelshopImportContentWriter.normalizeName("面单.json", "uuid", ".lsdx"));
    }

    @Test
    void normalizeName_replacesIllegalCharacters() {
        assertEquals("a_b_c.lsdx", LabelshopImportContentWriter.normalizeName("a/b:c", "uuid", ".lsdx"));
        assertEquals("a_b.lsdx", LabelshopImportContentWriter.normalizeName(" a*b ", "uuid", ".lsdx"));
    }

    @Test
    void normalizeName_fallsBackToRef() {
        assertEquals("uuid.lsdx", LabelshopImportContentWriter.normalizeName("", "uuid", ".lsdx"));
        assertEquals("uuid.lsdx", LabelshopImportContentWriter.normalizeName("   ", "uuid", ".lsdx"));
        assertEquals("uuid.json", LabelshopImportContentWriter.normalizeName(null, "uuid", ".json"));
    }

    @Test
    void normalizeName_truncatesTo255() {
        String normalized = LabelshopImportContentWriter.normalizeName("x".repeat(300), "uuid", ".lsdx");
        assertEquals(255, normalized.length());
        assertTrue(normalized.endsWith(".lsdx"));
    }

    @Test
    void write_preservesRawContentAndOwnerAttribution() {
        LabelshopSourceItem source = LabelshopSourceItem.builder()
                .sourceLibrary(1).sourceRef("p1").sourceCategory("企业/组织模板/云马")
                .sourceName("面单").format("lsdx").targetScope(2).targetOwnerId(88L).build();
        LabelshopImportItemDO item = LabelshopImportItemDO.builder()
                .id(77L).taskId(9L).sourceRef("p1").build();
        String content = "<?xml version=\"1.0\"?><labelshopdocument><labelform>"
                + "<label width=\"21000\" height=\"29700\" /></labelform></labelshopdocument>";

        writer.write(item, source, content, 1001L, 88L, 500L);

        ArgumentCaptor<MaxLabelShareContentDO> captor =
                ArgumentCaptor.forClass(MaxLabelShareContentDO.class);
        verify(contentMapper).insert(captor.capture());
        MaxLabelShareContentDO row = captor.getValue();
        assertEquals(2, row.getScope());
        assertEquals(1001L, row.getOwnerMemberId());
        assertEquals(88L, row.getOrgId());
        assertEquals(500L, row.getFolderId());
        assertEquals(1, row.getContentType());
        assertEquals(1, row.getStorageType());
        assertEquals(content, row.getContent());
        assertEquals("面单.lsdx", row.getName());
        assertEquals("text/xml", row.getMimeType());
        assertEquals("企业/组织模板/云马", row.getCategory());
        assertEquals(new BigDecimal("210.00"), row.getWidthMm());
        assertEquals(new BigDecimal("297.00"), row.getHeightMm());
        assertEquals((long) content.getBytes(StandardCharsets.UTF_8).length, row.getSizeBytes());
        assertNotNull(row.getContentHash());
        ArgumentCaptor<LabelshopImportItemDO> itemCaptor =
                ArgumentCaptor.forClass(LabelshopImportItemDO.class);
        verify(itemMapper).updateById(itemCaptor.capture());
        LabelshopImportItemDO saved = itemCaptor.getValue();
        assertEquals(1, saved.getStatus());
        assertTrue(Boolean.TRUE.equals(saved.getEditable()));
        assertEquals("lsdx", saved.getFormat());
    }

}
