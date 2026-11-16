package cn.iocoder.yudao.module.member.dal.dataobject.labelshop;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * MaxLabel LabelShop 导入任务 DO
 */
@TableName("maxlabel_labelshop_import_task")
@KeySequence("maxlabel_labelshop_import_task_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelshopImportTaskDO extends BaseDO {

    public static final int SUBJECT_ORG = 1;
    public static final int SUBJECT_MEMBER = 2;

    @TableId
    private Long id;
    /** 导入对象：1 企业 2 个人会员 */
    private Integer subjectType;
    /** 企业编号；个人会员任务为 NULL */
    private Long orgId;
    /** 导入目标会员编号：企业任务为目标 OWNER，个人任务为目标会员 */
    private Long ownerMemberId;
    /** 本次新建的企业文件夹编号（登录/枚举成功后回填） */
    private Long folderId;
    /** 计划新建的文件夹名称（<时间>导入） */
    private String folderName;
    /** LabelShop 来源账号掩码 */
    private String sourceAccountMasked;
    /** 状态：0 待执行 1 进行中 2 已完成 3 已失败 4 已中断 */
    private Integer status;
    /** 阶段：LOGIN / LIST / IMPORT / FINISH */
    private String phase;
    /** 枚举出的模板总数 */
    private Integer totalCount;
    /** 已处理数（成功+跳过+失败） */
    private Integer processedCount;
    /** 成功数 */
    private Integer successCount;
    /** 跳过数（幂等已存在） */
    private Integer skipCount;
    /** 失败数（仅模板级） */
    private Integer failCount;
    /** 写入个人空间字节数 */
    private Long personalBytes;
    /** 写入企业空间字节数 */
    private Long enterpriseBytes;
    /** 任务级失败原因（含来源级失败摘要；不含密码） */
    private String errorMessage;
    /** 后台操作人 system_users.id */
    private Long operatorId;
    /** 后台操作人昵称快照 */
    private String operatorName;
    /** 开始时间 */
    private LocalDateTime startedTime;
    /** 结束时间 */
    private LocalDateTime finishedTime;
    /** 心跳时间 */
    private LocalDateTime heartbeatTime;
    /** 进行中企业编号；终态置 NULL */
    private Long runningOrgId;
    /** 进行中个人会员编号；终态置 NULL */
    private Long runningMemberId;

}
