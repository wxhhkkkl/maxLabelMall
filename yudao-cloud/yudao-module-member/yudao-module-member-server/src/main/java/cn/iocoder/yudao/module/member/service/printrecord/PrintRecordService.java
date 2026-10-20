package cn.iocoder.yudao.module.member.service.printrecord;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.printrecord.vo.AdminPrintRecordPageReqVO;
import cn.iocoder.yudao.module.member.controller.app.printrecord.vo.AppPrintRecordCreateReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.printrecord.MaxLabelPrintRecordDO;

public interface PrintRecordService {

    Long create(Long memberId, AppPrintRecordCreateReqVO reqVO);

    PageResult<MaxLabelPrintRecordDO> getPage(AdminPrintRecordPageReqVO reqVO);

}
