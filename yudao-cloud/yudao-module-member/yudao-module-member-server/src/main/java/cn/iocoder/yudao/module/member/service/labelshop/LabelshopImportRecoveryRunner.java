package cn.iocoder.yudao.module.member.service.labelshop;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.member.dal.mysql.labelshop.LabelshopImportTaskMapper;
import jakarta.annotation.Resource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时把残留的待执行 / 进行中导入任务标记为中断。
 *
 * <p>本期单实例部署；多实例场景需改为按 heartbeat_time 判定。</p>
 */
@Component
public class LabelshopImportRecoveryRunner implements ApplicationRunner {

    @Resource
    private LabelshopImportTaskMapper taskMapper;

    @Override
    public void run(ApplicationArguments args) {
        TenantUtils.executeIgnore(() ->
                taskMapper.markActiveInterrupted("服务重启导致任务中断"));
    }

}
