package cn.iocoder.yudao.module.member.dal.dataobject.share;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * MaxLabel 平台共享内容 DO
 */
@TableName("maxlabel_platform_content")
@KeySequence("maxlabel_platform_content_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaxLabelPlatformContentDO extends BaseDO {

    @TableId
    private Long id;
    /** 内容类型：1 模板 2 txt 3 excel 4 csv 5 图片 */
    private Integer contentType;
    private String name;
    /** 存储方式：1 文本正文 2 文件地址 */
    private Integer storageType;
    private String content;
    private String fileUrl;
    private String mimeType;
    private Long sizeBytes;
    private String contentHash;
    private BigDecimal widthMm;
    private BigDecimal heightMm;
    private String category;
    private String keywords;
    /** 状态：1 上架 0 下架 */
    private Integer status;
    /** 发布管理员编号（system_users.id） */
    private Long publisherUserId;
    private LocalDateTime publishedTime;

}
