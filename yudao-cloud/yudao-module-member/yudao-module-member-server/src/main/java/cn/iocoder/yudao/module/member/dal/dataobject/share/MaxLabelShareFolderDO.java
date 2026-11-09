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
    /** 企业编号，关联 maxlabel_org.id */
    private Long orgId;
    /** 文件夹名称 */
    private String name;
    /** 创建的管理员会员编号 */
    private Long creatorMemberId;

}
