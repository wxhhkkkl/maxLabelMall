# 首页与服务支持页 · 第一版设计

首页的 Banner 与行业标签方案区已按第一版设计替换。其余首页内容与服务支持页暂时保留现状。

两页共同延续白色导航、品牌蓝、深蓝页脚和宽松留白，与软件介绍页保持一致。

首页：产品大图首屏 → 分类入口 → 商品展示 → 涂料、服装、仓储行业场景 → MaxLabel 开发中预告 → 服务支持入口。商品图片属于设计示意；实施时商品与分类沿用后台真实数据。

服务支持：搜索与设备操作图片首屏 → 四类帮助入口 → 按问题查找排查方向 → 设备基础使用指南 → 常见问题 → 联系支持。搜索、分类与指南为拟议结构，实施时建立对应检索行为及内容。没有资料时，不展示虚假下载入口。

软件仍显示开发中；不承诺上线日期、客服时段或质保期限，不虚构价格或销售数据。

文件：`home-v1.png`、`support-v1.png` 为完整设计图；`index.html` 可切换查看；`prompts.json` 保存内置 image_gen 的完整提示词。当前软件介绍页作为视觉参考。

首页 Banner 使用独立生成的高清图片，原图 `banner-original.png`，提示词 `banner-prompt.txt`，网页资源 `storefront/public/assets/home/banner.webp`。行业图复用软件页的高清涂料、服装、仓储配图。`implemented-home-*.png` 为两块区域在桌面与手机宽度的实际截图。

当前首页 Banner 已更新为包含软件的完整场景：`banner-software-v2.png`，内置 image_gen 重新生成；对应提示词 `banner-software-v2-prompt.txt`，网页资源 `storefront/public/assets/home/banner-software-v2.webp`。软件位于笔记本屏幕内，界面朦胧并标注开发中，打印机与耗材在同一场景中完整呈现。旧版独立软件窗口已移除。
