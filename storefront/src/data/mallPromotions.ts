const promotions = {
  labels: { title: '每一种标签，都有合适的选择', subtitle: '多种材质 · 按需选型', image: 'labels', color: '#b4dcef', alt: '多规格卷筒不干胶标签组合' },
  ribbon: { title: '好碳带，让打印更出色', subtitle: '匹配标签材质与打印需求', image: 'ribbon', color: '#f4e7d5', alt: '热转印黑色碳带卷' },
  printer: { title: '从第一张，到每一张', subtitle: '标签打印 · 高效有序', image: 'printer', color: '#bdd7eb', alt: '桌面标签打印机与标签纸' },
  sheet: { title: '日常标识，轻松安排', subtitle: '办公归档 · 产品标识', image: 'sheet', color: '#d3e9dc', alt: '多格模切的 A4 不干胶标签纸' },
  scanner: { title: '轻松一扫，连接业务', subtitle: '仓储出入库 · 商品采集', image: 'scanner', color: '#eadbc7', alt: '条码扫描枪与带条码的包装箱' },
  software: { title: '让标签设计极致简单', subtitle: '设计到打印 · 软件服务', image: 'software', color: '#b5c3f0', alt: '标签设计编辑器的示意画面' },
  rfid: { title: '让每件物品，有迹可循', subtitle: '智能识别 · 资产管理', image: 'rfid', color: '#c0eaec', alt: 'RFID 标签卷、电子标签与卡片' },
}

export function mallPromotion(name: string) {
  if (/RFID/i.test(name)) return promotions.rfid
  if (/A4|平张/.test(name)) return promotions.sheet
  if (/碳带/.test(name)) return promotions.ribbon
  if (/打印/.test(name)) return promotions.printer
  if (/扫描|采集/.test(name)) return promotions.scanner
  if (/软件/.test(name)) return promotions.software
  return promotions.labels
}
