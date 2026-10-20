package cn.iocoder.yudao.module.member.dal.dataobject.printrecord;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MaxLabel 会员打印记录 DO（不含业务数据快照）
 */
@TableName("maxlabel_print_record")
@KeySequence("maxlabel_print_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaxLabelPrintRecordDO extends BaseDO {

    @TableId
    private Long id;
    private Long memberId;
    private String deviceId;
    private String recordUid;
    private LocalDateTime printTime;
    private String templateTitle;
    private String mode;
    private Integer count;
    private Integer copies;
    private Long physicalCount;
    private Long sentCount;
    private String status;
    private String printer;

}
