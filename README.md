# 心愿便签（Wishboard）

适用于 Halo 2.20+ 的心愿墙与树洞插件，为站点提供便签展示、访客投稿、内容审核和主题集成能力。

## 功能概览

- **便签墙页面**：提供独立的心愿墙与树洞页面，便签以卡片形式展示，桌面端支持拖动，新便签发布后无需刷新即可显示
- **多种便签类型**：内置心愿和树洞两种类型，并支持在后台创建自定义类型
- **访客投稿**：访客可以匿名或署名发布便签，并选择便签颜色和类型
- **投稿开关**：可随时关闭前台投稿，保留已有便签的公开展示
- **内容审核**：支持免审核、人工审核和 AI 自动审核
- **AI 能力**：支持暖心回复、情绪标签、内容审核和投稿文案润色
- **心愿追踪**：支持待处理、进行中和已达成等状态，达成后可添加纪念照片与感言
- **纪念日计数**：可在便签墙显示纪念日天数和双方名称
- **访问保护**：支持访问频率限制和敏感词过滤
- **数据管理**：支持便签与类型的导入、导出、批量管理和审核
- **主题适配**：提供独立页面、主题模板覆盖和 Finder 数据访问能力

## 基本使用

安装并启用插件后，在插件设置中配置页面标题、投稿方式、审核模式和便签类型。

需要直接使用插件页面时，可启用内置页面并访问 `/wishes`。也可以在 Halo 后台创建便签墙页面，由当前主题提供 `wishes.html` 模板。

## 主题集成

### 使用主题模板

在主题的 `templates/` 目录中创建 `wishes.html`，插件会优先使用主题模板渲染便签墙。主题可以自行控制页面布局和样式，便签数据通过 `wishFinder` 获取。

### 显示便签

```html
<th:block th:if="${wishFinder != null}"
          th:with="wishes=${wishFinder.listApproved()}, typeMap=${wishFinder.getTypeMap()}">
  <article th:each="wish : ${wishes}">
    <span th:text="${typeMap[wish.spec.type]}">类型</span>
    <p th:text="${wish.spec.content}">便签内容</p>
    <span th:text="${wish.spec.anonymous ? '匿名' : wish.spec.nickname}">昵称</span>
  </article>
</th:block>
```

### 按类型显示

```html
<th:block th:if="${wishFinder != null}"
          th:with="wishes=${wishFinder.listByType('wish')}">
  <article th:each="wish : ${wishes}">
    <p th:text="${wish.spec.content}">心愿内容</p>
    <span th:text="${wish.spec.status}">状态</span>
  </article>
</th:block>
```

可将 `wish` 改为 `treehole` 或后台创建的自定义类型标识。主题还可以使用：

- `wishFinder.listTypes()`：获取便签类型
- `wishFinder.countApproved()`：获取公开便签总数
- `wishFinder.countByType('wish')`：获取指定类型数量
- `wishFinder.getTypeMap()`：获取类型名称映射

自定义 `wishes.html` 时，可直接使用插件注入的页面标题、副标题、纪念日设置、投稿开关和 AI 开关等模板变量。

## 交流

欢迎加入交流群了解更新、反馈问题和交流主题集成。

![交流群](./docs/community-group.png)

## 项目信息

- 版本：1.5.0
- 作者：Serenity
- 主页：https://www.aobp.cn
- 仓库：https://github.com/atangccc/Wishboard
- 许可证：GPL-3.0

## 二次开发与借鉴说明

如果你基于本项目进行二次开发、功能移植或实现思路借鉴，请主动告知开发者。这有助于了解项目的实际使用情况、协调兼容性，并减少重复开发。

上述告知属于社区协作倡议，不限制 GPL-3.0 已授予的复制、修改和再分发权利。发布或分发衍生作品时，仍需遵守 GPL-3.0 的源代码提供、许可证保留和版权声明要求。

如需联系开发者，请访问 https://www.aobp.cn 或 https://github.com/atangccc。
