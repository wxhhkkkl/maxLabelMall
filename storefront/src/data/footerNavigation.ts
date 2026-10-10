/** Only expose destinations that match their labels and current site capabilities. */
export const footerNavigation = [
  {
    title: '商城与采购',
    links: [
      { label: '全部商品', to: '/mall' },
      { label: '领券中心', to: '/coupon' },
      { label: '耗材选型', to: '/support?category=materials#knowledge' },
      { label: '企业采购咨询', to: '/support/contact' },
    ],
  },
  {
    title: '软件与支持',
    links: [
      { label: '标签软件介绍', to: '/software' },
      { label: '驱动安装指南', to: '/support/driver-install' },
      { label: '基础使用指南', to: '/support?category=guides#knowledge' },
      { label: '打印问题排查', to: '/support?category=troubleshooting#knowledge' },
    ],
  },
  {
    title: '行业方案',
    links: [
      { label: '仓储物流', to: '/solutions/warehouse' },
      { label: '生产制造', to: '/solutions/manufacturing' },
      { label: '医药与医疗器械', to: '/solutions/medical' },
      { label: '跨境电商', to: '/solutions/crossborder' },
      { label: '全部行业方案', to: '/solutions' },
    ],
  },
  {
    title: '关于与联系',
    links: [
      { label: '公司介绍', to: '/about' },
      { label: '联系我们', to: '/support/contact' },
      { label: '服务与支持', to: '/support' },
      { label: '个人中心', to: '/account' },
    ],
  },
] as const
