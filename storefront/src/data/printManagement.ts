// Based on labelManagementSystem's implemented admin routes and userManual.ts.
// Screenshots are the source project's manual examples, not customer production data.
export const printManagementScreens = [
  { id: 'dashboard', label: '码量看板', eyebrow: '看清当前码量', title: '分配到哪里，还剩多少，一眼清楚', text: '按公司、产品或公司与产品组合查看待分配、已分配未打印、已打印、已作废的数量，并支持导出码量报表。', points: ['集中查看当前码状态分布', '按码入库时间筛选统计范围', '已分配未打印包含正在打印的码'], image: 'admin-dashboard', alt: '码量看板：按公司与产品查看码数量分布' },
  { id: 'template', label: '模板与版本', eyebrow: '总部统一版式', title: '模板统一维护，分配时保留版本', text: '在可视化编辑器中配置标签尺寸、二维码、条码、文本、动态字段、日期与流水号。保存形成模板版本，新分配使用产品绑定的模板版本。', points: ['拖动排版、坐标微调与打印预览', '分配单锁定模板版本，已有分配不随编辑改变', '首次打印生成日期与流水号，补打沿用原值'], image: 'admin-template-editor', alt: '标签模板编辑器：尺寸、图层与动态字段配置' },
  { id: 'workstation', label: '工位打印', eyebrow: '现场专注执行', title: '选好产品与数量，按分配模板打印', text: 'Windows 工位客户端绑定本机工位，选择本地打印机。操作员查看可打印数量与下一张码对应的模板，核对后提交打印任务。', points: ['公司内可用工位共享已分配未打印码', '打印机选择保存，提供独立测试打印', '查看本次任务结果，现场核对实物标签'], image: 'print-workspace', alt: '工位打印客户端：产品、数量、模板预览和任务结果' },
  { id: 'reprint', label: '受控补打', eyebrow: '异常有处理路径', title: '补打有原因，任务回到原工位', text: '管理员填写已打印码与补打原因，系统向最近一次打印的原工位派发任务。原工位在线且可用时，客户端领取任务，由操作员核对并执行。', points: ['普通首次打印不能重复使用已打印码', '原工位离线或停用时拒绝派发补打', '补打保留首次打印日期与流水号'], image: 'print-reprint', alt: '工位客户端的补打任务列表' },
  { id: 'trace', label: '单码追溯', eyebrow: '过程可查询', title: '从导入到打印，沿着一个码查经过', text: '输入完整码值，查看当前状态、所属公司、分配单及打印与补打次数，通过时间线核对操作人员、工位、时间和原因。', points: ['串联导入、分配、打印、补打与作废事件', '打印明细与补打作废台账分别留档', '管理操作日志辅助核查操作过程'], image: 'admin-timeline', alt: '单码追溯页面：码状态摘要与事件时间线' },
]

export const printManagementRoles = [
  { icon: 'software', title: '总部管理员', text: '统一维护组织、产品与模板，导入和分配码，查看全局记录与追溯。', tag: '统一配置与管理' },
  { icon: 'order', title: '分公司管理员', text: '管理本公司工位与员工，查询本公司记录，并发起补打。', tag: '按公司范围协作' },
  { icon: 'printer', title: '工位操作员', text: '在绑定的电脑上执行首次打印与补打，检查任务结果和实物标签。', tag: '现场打印与核对' },
]

export const printManagementFlow = [
  { title: '准备产品与模板', text: '总部建立产品、编辑版式并绑定模板。' },
  { title: '导入码与生产批号', text: '上传 Excel，核查导入结果与失败明细。' },
  { title: '分配到公司', text: '分配可用码，并记录所用模板版本。' },
  { title: '工位执行打印', text: '核对产品、模板与数量后提交任务。' },
  { title: '按需处理补打', text: '填写原因，原工位领取并执行任务。' },
  { title: '查询记录与追溯', text: '通过明细、台账与单码时间线核查。' },
]
