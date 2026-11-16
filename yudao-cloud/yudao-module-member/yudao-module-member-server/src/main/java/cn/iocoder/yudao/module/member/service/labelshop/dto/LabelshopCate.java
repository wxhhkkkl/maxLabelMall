package cn.iocoder.yudao.module.member.service.labelshop.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LabelShop 分类树节点。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LabelshopCate {

    private Long cateId;
    private Long parentId;
    private String name;
    /** 分类自身的 Own_Type；用于收窄列表候选值（11 = LabelShop5 老账号） */
    private Integer ownType;

    public LabelshopCate(Long cateId, Long parentId, String name) {
        this(cateId, parentId, name, null);
    }

}
