package cn.iocoder.yudao.module.member.dal.dataobject.share;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * MaxLabel 企业文件夹可见成员 DO（名单变更时物理删除）
 */
@TableName("maxlabel_share_folder_member")
@KeySequence("maxlabel_share_folder_member_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaxLabelShareFolderMemberDO extends BaseDO {

    @TableId
    private Long id;
    /** 文件夹编号，关联 maxlabel_share_folder.id */
    private Long folderId;
    /** 可见会员编号，关联 member_user.id */
    private Long memberId;

}
