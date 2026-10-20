package cn.iocoder.yudao.module.member.controller.app.printrecord;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.member.controller.app.printrecord.vo.AppPrintRecordCreateReqVO;
import cn.iocoder.yudao.module.member.service.printrecord.PrintRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "用户 App - MaxLabel 打印记录")
@RestController
@RequestMapping("/member/print-record")
@Validated
public class AppPrintRecordController {

    @Resource
    private PrintRecordService printRecordService;

    @PostMapping("/create")
    @Operation(summary = "上报打印记录")
    public CommonResult<Map<String, Long>> create(@Valid @RequestBody AppPrintRecordCreateReqVO reqVO) {
        Long id = printRecordService.create(SecurityFrameworkUtils.getLoginUserId(), reqVO);
        return success(Map.of("id", id));
    }

}
