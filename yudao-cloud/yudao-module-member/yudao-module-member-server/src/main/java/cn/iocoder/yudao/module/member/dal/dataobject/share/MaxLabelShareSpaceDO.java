package cn.iocoder.yudao.module.member.dal.dataobject.share;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * MaxLabel 共享空间配额锁 DO
 *
 * 一行 = 一个个人空间或企业空间；只用于加锁与缓存用量，上限由版本/授权实时推导。
 */
@TableName("maxlabel_share_space")
@KeySequence("maxlabel_share_space_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaxLabelShareSpaceDO extends BaseDO {

    @TableId
    private Long id;
    /** 空间类型：1 个人 2 企业 */
    private Integer scope;
    /** 归属编号：个人为 member_user.id，企业为 maxlabel_org.id */
    private Long ownerId;
    /** 已用字节数（锁内按内容表重算后回写） */
    private Long usedBytes;

}
