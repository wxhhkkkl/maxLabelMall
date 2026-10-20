package cn.iocoder.yudao.module.member.controller.admin.printrecord.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 会员打印记录 Response VO")
@Data
public class AdminPrintRecordRespVO {

    private Long id;
    private Long memberId;
    private String deviceId;
    private String recordUid;
    private LocalDateTime printTime;
    private String templateTitle;
    private String mode;
    private Integer count;
    private Integer copies;
    private Long physicalCount;
    private Long sentCount;
    private String status;
    private String printer;
    private LocalDateTime createTime;

}
