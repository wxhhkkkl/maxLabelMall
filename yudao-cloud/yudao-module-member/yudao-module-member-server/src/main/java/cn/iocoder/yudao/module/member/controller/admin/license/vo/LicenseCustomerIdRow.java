package cn.iocoder.yudao.module.member.controller.admin.license.vo;

import lombok.Data;

/**
 * 明细分页的**中间行**：SQL 只返回 id 与类型，展示字段由服务层按这一页装配
 * （避免把整表行对象搬进内存）。
 */
@Data
public class LicenseCustomerIdRow {

    private Integer targetType;
    private Long targetId;

}
