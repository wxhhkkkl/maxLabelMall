package cn.iocoder.yudao.module.member.service.license;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseCustomerPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseCustomerRespVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGiftTargetPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseGiftTargetRespVO;
import cn.iocoder.yudao.module.member.controller.admin.license.vo.LicenseSummaryRespVO;

/**
 * 授权看板查询 Service（020）
 *
 * 口径详见 `specs/020-license-dashboard/data-model.md` §4：
 * · 有效 = 个人 `end_time > now`；企业 = 启用 + 有有效批次（不看席位是否分配，Q156）
 * · 当年三个数字 = 新获得 / 延续 / 合计（按客户去重，Q155），一律用 `create_time` 判定年份
 */
public interface LicenseQueryService {

    /**
     * 看板总览
     */
    LicenseSummaryRespVO getSummary();

    /**
     * 客户授权明细（一客户一行，双来源分别给出并合并取较晚到期）
     */
    PageResult<LicenseCustomerRespVO> getCustomerPage(LicenseCustomerPageReqVO reqVO);

    /**
     * 赠送对象分页：全部个人与企业，**不限是否已有授权**（Q162）
     */
    PageResult<LicenseGiftTargetRespVO> getGiftTargetPage(LicenseGiftTargetPageReqVO reqVO);

}
