export interface CoatingExample { name: string; color: string; batch: string; swatch: string }
/** 涂料企业的概念示例，表格与标签共用，避免字段错配。 */
export const coatingExamples: CoatingExample[] = [
  { name: '水性底漆', color: 'RAL7035', batch: 'TL-001', swatch: '#B4C0D8' },
  { name: '水性面漆', color: 'RAL9010', batch: 'TL-002', swatch: '#F0F4FB' },
  { name: '罩光清漆', color: '透明', batch: 'TL-003', swatch: '#E3E9F4' },
]
export const softwareExperiences = [
  { title: '直观地设计', description: '文字、条码与图片，在画布中清楚组织。', path: 'M14 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-9M16 3l5 5M9 15l2-5L19 2l3 3-8 8-5 2Z' },
  { title: '有序地准备数据', description: '让单据字段与标签内容建立对应。', path: 'M3 3h18v18H3V3ZM3 9h18M9 9v12M15 9v12M3 15h18' },
  { title: '从容地完成打印', description: '先看预览，再按照设置输出。', path: 'M6 9V3h12v6M6 17H3V9h18v8h-3M6 14h12v7H6v-7ZM17 12h1' },
]
export const softwareApplications = [
  { title: '涂料产品标', description: '型号、色号与批次，随成品一起交付。', image: '/assets/software/paint.webp', alt: '蓝盖白色涂料桶，标签展示型号 WX-210、色号 RAL7035 与批次 TL-001' },
  { title: '服装吊牌', description: '款号、尺码与成分，清楚展示。', image: '/assets/software/apparel.webp', alt: '深蓝色 T 恤上的白色吊牌，展示款号、L 码与棉质成分' },
  { title: '仓储库位标', description: '库区、货架与层位，有序对应。', image: '/assets/software/warehouse.webp', alt: '蓝色周转箱上的 A-01-03 库位标，展示 A 区、01 货架和 03 层位' },
]
