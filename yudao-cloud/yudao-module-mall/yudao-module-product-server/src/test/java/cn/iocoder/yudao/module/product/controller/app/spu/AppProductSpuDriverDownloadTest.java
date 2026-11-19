package cn.iocoder.yudao.module.product.controller.app.spu;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.api.org.MemberOrgApi;
import cn.iocoder.yudao.module.product.controller.app.spu.vo.AppProductSpuDetailRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareDO;
import cn.iocoder.yudao.module.product.dal.dataobject.software.SoftwareVersionDO;
import cn.iocoder.yudao.module.product.dal.dataobject.spu.ProductSpuDO;
import cn.iocoder.yudao.module.product.service.history.ProductBrowseHistoryService;
import cn.iocoder.yudao.module.product.service.sku.ProductSkuService;
import cn.iocoder.yudao.module.product.service.software.SoftwareService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuBrowseLevelService;
import cn.iocoder.yudao.module.product.service.spu.ProductSpuService;
import cn.iocoder.yudao.module.product.service.subscriptionplan.MaxLabelSubscriptionPlanService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * 商品详情「下载驱动 / 下载软件」（2026-11-18）
 *
 * 钉住三件事：
 * · 关联了启用的软件且有当前发布 → 详情带上下载直链与类型（前端据此决定按钮文案）
 * · 软件停用 / 没有当前发布 → 不返回下载信息（前端不显示入口，不报错）
 * · 没关联 → 同样不返回
 */
@ExtendWith(MockitoExtension.class)
public class AppProductSpuDriverDownloadTest {

    @Mock
    private ProductSpuService productSpuService;
    @Mock
    private ProductSkuService productSkuService;
    @Mock
    private ProductBrowseHistoryService productBrowseHistoryService;
    @Mock
    private MemberOrgApi memberOrgApi;
    @Mock
    private MaxLabelSubscriptionPlanService subscriptionPlanService;
    @Mock
    private ProductSpuBrowseLevelService browseLevelService;
    @Mock
    private SoftwareService softwareService;
    @InjectMocks
    private AppProductSpuController controller;

    private ProductSpuDO spu(Long driverSoftwareId) {
        return ProductSpuDO.builder()
                .id(7L).name("先擎 Zenpert 4T520").status(1) // 1 = 上架
                .enterpriseOnly(false).browseLevelType(0)
                .salesCount(0).virtualSalesCount(0)
                .driverSoftwareId(driverSoftwareId)
                .build();
    }

    private SoftwareDO software(Integer status) {
        SoftwareDO software = new SoftwareDO();
        software.setId(2L);
        software.setTitle("zenpert4T530");
        software.setType(2);
        software.setStatus(status);
        return software;
    }

    private SoftwareVersionDO version() {
        SoftwareVersionDO version = new SoftwareVersionDO();
        version.setVersion("7.4.2");
        version.setFileUrl("https://img.example.com/software/zenpert-7.4.2.zip");
        return version;
    }

    private AppProductSpuDetailRespVO getDetail(ProductSpuDO spu) {
        when(productSpuService.getSpu(7L)).thenReturn(spu);
        when(productSkuService.getSkuListBySpuId(7L)).thenReturn(List.of());
        CommonResult<AppProductSpuDetailRespVO> result = controller.getSpuDetail(7L);
        assertEquals(0, result.getCode());
        return result.getData();
    }

    @Test
    public void testGetSpuDetail_driverLinked() {
        when(softwareService.getSoftware(2L)).thenReturn(software(0));
        when(softwareService.getLatestCurrentVersion(2L)).thenReturn(version());

        AppProductSpuDetailRespVO vo = getDetail(spu(2L));

        assertEquals(2L, vo.getDriverSoftwareId());
        assertEquals("zenpert4T530", vo.getDriverTitle());
        assertEquals(2, vo.getDriverType());
        assertEquals("7.4.2", vo.getDriverVersion());
        assertEquals("https://img.example.com/software/zenpert-7.4.2.zip", vo.getDriverDownloadUrl());
    }

    @Test
    public void testGetSpuDetail_driverDisabled_notExposed() {
        when(softwareService.getSoftware(2L)).thenReturn(software(1));

        AppProductSpuDetailRespVO vo = getDetail(spu(2L));

        assertNull(vo.getDriverDownloadUrl());
        assertNull(vo.getDriverTitle());
    }

    @Test
    public void testGetSpuDetail_driverWithoutCurrentVersion_notExposed() {
        when(softwareService.getSoftware(2L)).thenReturn(software(0));
        when(softwareService.getLatestCurrentVersion(2L)).thenReturn(null);

        AppProductSpuDetailRespVO vo = getDetail(spu(2L));

        assertNull(vo.getDriverDownloadUrl());
    }

    @Test
    public void testGetSpuDetail_noDriverLink() {
        AppProductSpuDetailRespVO vo = getDetail(spu(null));

        assertNull(vo.getDriverDownloadUrl());
        assertNull(vo.getDriverSoftwareId());
    }

}
