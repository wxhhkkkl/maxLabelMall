package cn.iocoder.yudao.module.member.service.labelshop.dto;

import lombok.Data;

import java.util.List;

/**
 * 一次 LabelShop 登录的会话（只在任务内存中使用，不落库）。
 */
@Data
public class LabelshopSession {

    private String token;
    private Long uid;
    private String mid;
    private List<LabelshopCompany> companies;

}
