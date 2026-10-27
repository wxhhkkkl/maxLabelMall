package cn.iocoder.yudao.module.product.dal.dataobject.software;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MaxLabel 软件版本 DO（013）
 */
@TableName("maxlabel_software_version")
@KeySequence("maxlabel_software_version_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoftwareVersionDO extends BaseDO {

    @TableId
    private Long id;
    /** 所属软件，关联 maxlabel_software.id */
    private Long softwareId;
    /** 版本号（语义化三段式） */
    private String version;
    /** 平台：windows / macos / linux */
    private String platform;
    /** 架构：x64 / arm64 / x86 */
    private String arch;
    /** 原始文件名 */
    private String fileName;
    /** 文件大小（字节） */
    private Long fileSize;
    /** Base64 编码的 sha512 */
    private String sha512;
    /** 对象存储公开永久直链 */
    private String fileUrl;
    /** 对象键 */
    private String objectKey;
    /** 发布说明 */
    private String releaseNotes;
    /** 发布日期 */
    private LocalDateTime releaseTime;
    /** 1 = 当前发布；NULL = 非当前（依赖唯一索引 uk_current 保证唯一） */
    private Integer currentFlag;
    /** 状态：0 已发布 1 草稿 2 已下架，见 SoftwareVersionStatusEnum */
    private Integer status;

}
