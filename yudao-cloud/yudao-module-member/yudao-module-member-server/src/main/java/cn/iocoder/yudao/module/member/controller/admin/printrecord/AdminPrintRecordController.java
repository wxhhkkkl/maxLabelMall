package cn.iocoder.yudao.module.member.controller.admin.printrecord;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.member.controller.admin.printrecord.vo.AdminPrintRecordPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.printrecord.vo.AdminPrintRecordRespVO;
import cn.iocoder.yudao.module.member.dal.dataobject.printrecord.MaxLabelPrintRecordDO;
import cn.iocoder.yudao.module.member.service.printrecord.PrintRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - MaxLabel 打印记录")
@RestController
@RequestMapping("/member/print-record")
@Validated
public class AdminPrintRecordController {

    @Resource
    private PrintRecordService printRecordService;

    @GetMapping("/page")
    @Operation(summary = "获得会员打印记录分页")
    @PreAuthorize("@ss.hasPermission('member:print-record:query')")
    public CommonResult<PageResult<AdminPrintRecordRespVO>> getPage(@Valid AdminPrintRecordPageReqVO reqVO) {
        PageResult<MaxLabelPrintRecordDO> page = printRecordService.getPage(reqVO);
        List<AdminPrintRecordRespVO> list = page.getList().stream()
                .map(record -> BeanUtils.toBean(record, AdminPrintRecordRespVO.class)).toList();
        return success(new PageResult<>(list, page.getTotal()));
    }

}
