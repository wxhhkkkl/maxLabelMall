package cn.iocoder.yudao.module.member.controller.admin.license.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 赠送对象分页的**中间行**：SQL 直接带出展示字段与到期时间，
 * 服务层只负责把到期时间换算成剩余天数与可赠送月数。
 */
@Data
public class LicenseGiftTargetRow {

    private Integer targetType;
    private Long targetId;
    private String targetName;
    private String targetMobile;
    private Integer status;
    /** 原始到期时间（次日 00:00），展示前 −1 天 */
    private LocalDateTime endTime;

}
