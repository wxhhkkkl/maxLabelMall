package cn.iocoder.yudao.module.member.dal.dataobject.labelshop;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * MaxLabel LabelShop 导入明细 DO
 */
@TableName("maxlabel_labelshop_import_item")
@KeySequence("maxlabel_labelshop_import_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelshopImportItemDO extends BaseDO {

    @TableId
    private Long id;
    /** 任务编号 */
    private Long taskId;
    /** 来源库：12 我的模板库 13 组模板库 1 企业/组织模板 */
    private Integer sourceLibrary;
    /** LabelShop 模板 UUID */
    private String sourceRef;
    /** 来源分类路径 */
    private String sourceCategory;
    /** 来源模板名称（原始） */
    private String sourceName;
    /** 目标空间：1 个人 2 企业 */
    private Integer targetScope;
    /** 目标归属：个人为 OWNER 会员编号，企业为 org_id */
    private Long targetOwnerId;
    /** 写入的 maxlabel_share_content.id */
    private Long contentId;
    /** 格式：lsdx / cloud-json */
    private String format;
    /** 是否可编辑：lsdx=1，企业私有 JSON=0 */
    private Boolean editable;
    /** 写入字节数 */
    private Long sizeBytes;
    /** 状态：0 待处理 1 成功 2 跳过 3 失败 */
    private Integer status;
    /** 失败原因（不含密码） */
    private String errorMessage;

}
