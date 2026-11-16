package cn.iocoder.yudao.module.member.dal.dataobject.share;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * MaxLabel 企业共享文件夹 DO（单层）
 */
@TableName("maxlabel_share_folder")
@KeySequence("maxlabel_share_folder_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaxLabelShareFolderDO extends BaseDO {

    @TableId
    private Long id;
    /** 空间类型：1 个人 2 企业 */
    private Integer scope;
    /** 个人文件夹归属会员编号；企业文件夹为 NULL */
    private Long ownerMemberId;
    /** 父文件夹编号；NULL = 根目录 */
    private Long parentId;
    /** 企业编号，关联 maxlabel_org.id；个人文件夹为 NULL */
    private Long orgId;
    /** 文件夹名称 */
    private String name;
    /** 创建的管理员会员编号 */
    private Long creatorMemberId;

}
