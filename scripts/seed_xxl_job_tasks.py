"""把代码里实际存在的 @XxlJob handler 灌进 XXL-Job 的 xxl_job_info（2026-10-08）。

为什么要脚本而不是在控制台点 30 次：handler 名必须和 `@XxlJob("...")` 的字符串
**逐字一致**，手抄容易错；而 `infra_job` 那 27 行种子数据里有 6 个 handler 在代码里
已经没有实现了（CMS 模块没了），照着它抄会建出一堆永远找不到执行器的任务。

数据来源：
  · handler 名 —— grep 仓库里的 `@XxlJob("...")`（**代码是唯一权威**）
  · 任务名 / 默认 cron —— 尽量沿用上游 SQL 种子里同名 handler 的值，
    没有的用下面 DEFAULTS 里显式写的值（**不猜 cron，写不出就给个整点值并标出来**）

⚠️ **所有任务一律 `trigger_status=0`（停止）**。所有者 2026-10-08 决策：
全部建好但默认不跑，避免"装完就自己动数据"。

用法：
    python scripts/seed_xxl_job_tasks.py            # dry-run
    python scripts/seed_xxl_job_tasks.py --apply
"""
import argparse
import json
import re

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from _dbtunnel import DBTunnel, _env  # noqa: E402

import pymysql  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
UPSTREAM_SQL = ROOT / 'yudao-cloud' / 'sql' / 'mysql' / 'ruoyi-vue-pro.sql'
APP_NAME = 'yudao-server'      # = spring.application.name，执行器注册用这个名字
GROUP_TITLE = '赋签商城'

# ⚠️ 这些 handler 在**线上这个部署里永远跑不了**，所以不建任务（所有者 2026-10-08 确认删掉）。
# 原因：线上 jar 只打了 26 个模块，ai / iot / hrm / im / oa / pms **没有打包进去**，
# 执行器启动时压根不会注册它们（实测注册成功的是 18 个，不是 30 个）。
# 在控制台建了也只是个点了会报「找不到执行器」的死任务。
# 将来这些模块打进 jar 了，把这里的名字删掉再跑本脚本即可。
NOT_IN_DEPLOYED_JAR = {
    'aiMidjourneySyncJob', 'aiSunoSyncJob',                    # ai
    'deviceOfflineCheckJob', 'deviceUpgradeJob',               # iot
    'hrmEmployeeChangeJob', 'hrmPerformanceAppealTimeoutJob',
    'hrmSalaryChangeJob',                                      # hrm
    'imRtcCallCleanupJob', 'imRtcParticipantTimeoutJob',       # im
    'oaMeetingRoomBookingReminderJob', 'oaScheduleReminderJob', # oa
    'pmsKnowledgeRecycleCleanJob',                             # pms
}

# 上游种子里没有、或名字对不上的，在这里显式给默认值。**没写的一律报错，不静默兜底。**
DEFAULTS = {
    'tokenCleanJob': ('过期 Token 清理 Job', '0 0 0 * * ?'),
    'tradeStatisticsJob': ('Mall 交易统计 Job', '0 0 0 * * ?'),
    'aiMidjourneySyncJob': ('AI Midjourney 同步 Job', '0 0/1 * * * ?'),
    'aiSunoSyncJob': ('AI Suno 同步 Job', '0 0/1 * * * ?'),
    'customerAutoPutPoolJob': ('CRM 客户自动掉公海 Job', '0 0 0 * * ?'),
    'hrmSalaryChangeJob': ('HRM 薪资调整生效 Job', '0 10 0 * * ?'),
    'hrmEmployeeChangeJob': ('HRM 员工生命周期生效 Job', '0 5 0 * * ?'),
    'oaScheduleReminderJob': ('OA 日程提醒 Job', '0 * * * * ?'),
    'oaMeetingRoomBookingReminderJob': ('OA 会议室预订提醒 Job', '0 * * * * ?'),
    'imRtcCallCleanupJob': ('IM 僵尸通话清理 Job', '0 * * * * ?'),
    'imRtcParticipantTimeoutJob': ('IM 通话参与者超时 Job', '0 * * * * ?'),
    # ⚠️ 上游种子写的是 iotDeviceOfflineCheckJob / iotOtaUpgradeJob，
    #    但代码里 @XxlJob 实际是下面这两个名字 —— 以代码为准。
    'deviceOfflineCheckJob': ('IoT 设备离线检查 Job', '0 * * * * ?'),
    'deviceUpgradeJob': ('IoT OTA 升级推送 Job', '0 * * * * ?'),
}


def handlers_from_source():
    """扫仓库里所有 @XxlJob("名字") —— 这是唯一权威来源。

    用 Python 走目录而不是 shell 出去 grep：这台开发机是 Windows，
    子进程里的 grep 参数/路径在 git-bash 和 cmd 之间行为不一致（踩过 exit 2）。
    """
    names = set()
    for path in (ROOT / 'yudao-cloud').rglob('*.java'):
        if 'target' in path.parts:
            continue
        try:
            text = path.read_text(encoding='utf-8', errors='replace')
        except OSError:
            continue
        names.update(re.findall(r'@XxlJob\("([^"]+)"\)', text))
    return sorted(names)


def upstream_seed():
    """从上游 SQL 里抠出 handler_name → (任务名, cron)。"""
    seed = {}
    text = UPSTREAM_SQL.read_text(encoding='utf-8', errors='replace')
    for line in text.splitlines():
        if 'INSERT INTO `infra_job`' not in line:
            continue
        # VALUES (5, '支付通知 Job', 2, 'payNotifyJob', NULL, '* * * * * ?', ...)
        m = re.search(r"VALUES \((\d+), '([^']*)', (\d+), '([^']*)', (?:NULL|'[^']*'), '([^']*)'", line)
        if m:
            _, desc, _, handler, cron = m.groups()
            seed[handler] = (desc, cron)
    return seed


def build_rows():
    handlers = [h for h in handlers_from_source() if h not in NOT_IN_DEPLOYED_JAR]
    seed = upstream_seed()
    rows, unknown = [], []
    for h in handlers:
        if h in seed:
            desc, cron = seed[h]
        elif h in DEFAULTS:
            desc, cron = DEFAULTS[h]
        else:
            unknown.append(h)
            continue
        rows.append({'handler': h, 'desc': desc, 'cron': cron,
                     'from_seed': h in seed})
    if unknown:
        raise SystemExit('✗ 这些 handler 没有默认 cron，请补进 DEFAULTS：%s' % unknown)
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--apply', action='store_true')
    args = ap.parse_args()

    rows = build_rows()
    print('从源码里找到 %d 个 @XxlJob handler' % len(rows))
    print('其中 %d 个沿用上游 SQL 种子的 cron，%d 个用脚本里的默认值'
          % (sum(r['from_seed'] for r in rows), sum(not r['from_seed'] for r in rows)))
    print()
    print('%-38s %-28s %s' % ('handler', '任务名', 'cron'))
    for r in rows:
        print('%-38s %-28s %s' % (r['handler'], r['desc'], r['cron']))

    if not args.apply:
        print()
        print('（dry-run，未写库。加 --apply 执行）')
        return

    env = _env()
    with DBTunnel() as (h, p):
        c = pymysql.connect(host=h, port=p, user='root',
                            password=env['MYSQL_DB_SERVER_PASSWRD'],
                            database='xxl_job', charset='utf8mb4', autocommit=False)
        cur = c.cursor()
        cur.execute('SELECT id FROM xxl_job_group WHERE app_name=%s', (APP_NAME,))
        got = cur.fetchone()
        if got:
            gid = got[0]
        else:
            cur.execute(
                'INSERT INTO xxl_job_group (app_name,title,address_type,address_list,update_time) '
                'VALUES (%s,%s,0,NULL,NOW())', (APP_NAME, GROUP_TITLE))
            gid = cur.lastrowid
        print('\n执行器分组 id =', gid, '(', APP_NAME, ')')

        cur.execute('SELECT executor_handler FROM xxl_job_info')
        existing = {r[0] for r in cur.fetchall()}
        added = 0
        for r in rows:
            if r['handler'] in existing:
                continue
            cur.execute(
                # ⚠️ glue_updatetime / glue_source **绝不能留 NULL**：
                #    XxlJobTrigger.processTrigger 里是 `jobInfo.getGlueUpdatetime().getTime()`，
                #    没有判空 —— 留 NULL 的话点「执行一次」会在 Admin 侧 NPE，
                #    日志里只看到 trigger_code=0、任务永远不执行（第一次就是这么踩的）。
                #    上游示例行给的是 '2018-11-03 22:21:31' + glue_source=''。
                'INSERT INTO xxl_job_info (job_group,job_desc,add_time,update_time,author,'
                'alarm_email,schedule_type,schedule_conf,misfire_strategy,executor_route_strategy,'
                'executor_handler,executor_param,executor_block_strategy,executor_timeout,'
                'executor_fail_retry_count,glue_type,glue_source,glue_remark,glue_updatetime,'
                'child_jobid,trigger_status,trigger_last_time,trigger_next_time) '
                'VALUES (%s,%s,NOW(),NOW(),%s,NULL,%s,%s,%s,%s,%s,NULL,%s,0,0,%s,%s,%s,NOW(),'
                'NULL,0,0,0)',
                (gid, r['desc'], 'maxlabel', 'CRON', r['cron'], 'DO_NOTHING',
                 'FIRST', r['handler'], 'SERIAL_EXECUTION', 'BEAN', '', 'GLUE代码初始化'))
            added += 1
        c.commit()
        cur.execute('SELECT COUNT(*), SUM(trigger_status=1) FROM xxl_job_info')
        total, running = cur.fetchone()
        print('✓ 新增 %d 个任务；库里现有 %d 个，其中启用的 %d 个' % (added, total, running or 0))
        c.close()


if __name__ == '__main__':
    main()
