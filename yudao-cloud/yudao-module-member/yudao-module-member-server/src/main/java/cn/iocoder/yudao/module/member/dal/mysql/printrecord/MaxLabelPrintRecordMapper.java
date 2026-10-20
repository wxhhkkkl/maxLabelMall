package cn.iocoder.yudao.module.member.dal.mysql.printrecord;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.controller.admin.printrecord.vo.AdminPrintRecordPageReqVO;
import cn.iocoder.yudao.module.member.dal.dataobject.printrecord.MaxLabelPrintRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MaxLabelPrintRecordMapper extends BaseMapperX<MaxLabelPrintRecordDO> {

    default MaxLabelPrintRecordDO selectByMemberIdAndRecordUid(Long memberId, String recordUid) {
        return selectOne(new LambdaQueryWrapperX<MaxLabelPrintRecordDO>()
                .eq(MaxLabelPrintRecordDO::getMemberId, memberId)
                .eq(MaxLabelPrintRecordDO::getRecordUid, recordUid));
    }

    default PageResult<MaxLabelPrintRecordDO> selectPageByMemberId(AdminPrintRecordPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MaxLabelPrintRecordDO>()
                .eq(MaxLabelPrintRecordDO::getMemberId, reqVO.getUserId())
                .betweenIfPresent(MaxLabelPrintRecordDO::getPrintTime, reqVO.getStartTime(), reqVO.getEndTime())
                .orderByDesc(MaxLabelPrintRecordDO::getPrintTime));
    }

}
