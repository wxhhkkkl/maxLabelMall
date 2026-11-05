package cn.iocoder.yudao.module.member.dal.dataobject.share;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;

/**
 * MaxLabel 个人 / 企业共享内容 DO
 */
@TableName("maxlabel_share_content")
@KeySequence("maxlabel_share_content_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaxLabelShareContentDO extends BaseDO {

    @TableId
    private Long id;
    /** 空间类型：1 个人 2 企业 */
    private Integer scope;
    /** 上传者 / 个人空间归属会员编号 */
    private Long ownerMemberId;
    /** 企业编号；个人空间为 NULL */
    private Long orgId;
    /** 文件夹编号；企业空间必填，个人空间为 NULL */
    private Long folderId;
    /** 内容类型：1 模板 2 txt 3 excel 4 csv 5 图片 */
    private Integer contentType;
    /** 名称（含扩展名） */
    private String name;
    /** 存储方式：1 文本正文 2 文件地址 */
    private Integer storageType;
    /** 文本正文（模板 / txt / csv） */
    private String content;
    /** 对象存储访问地址（excel / 图片） */
    private String fileUrl;
    private String mimeType;
    /** 字节大小（计入配额） */
    private Long sizeBytes;
    /** 文本正文 SHA-256 */
    private String contentHash;
    private BigDecimal widthMm;
    private BigDecimal heightMm;
    private String category;
    private String keywords;

}
