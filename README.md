# 心愿便签 (Wishboard)

适用于 [Halo](https://halo.run/) 的心愿墙 + 树洞插件，为你的站点增添一面温暖的便签墙。

## 功能概览

- **便签墙页面** — 独立的 `/wishes` 页面，便签以卡片形式散落展示，桌面端支持拖拽移动，新便签即时上墙无需刷新
- **多种便签类型** — 内置「心愿」和「树洞」两种类型，支持自定义扩展更多类型
- **访客投稿** — 访客可匿名或署名发布便签，支持选择颜色和类型
- **投稿开关** — 后台可一键关闭前端投稿功能，关闭后隐藏输入框且后端 API 返回 403，防止绕过前端直接调用
- **内容审核** — 支持三种审核模式：免审核 / 人工审核 / AI 自动审核
- **AI 能力** — 接入 OpenAI 兼容接口（OpenAI / DeepSeek / 通义千问等），提供：
  - 暖心回复：自动为便签生成温暖的 AI 回复
  - 情绪标签：识别内容情绪并标注 emoji
  - 内容审核：AI 自动判断是否包含违规内容（仅审核通过后才消耗 AI 额度）
  - 文案润色：访客发布前可一键 AI 润色
- **心愿追踪** — 所有类型便签均支持状态流转：待处理 → 进行中 → 已达成，达成后可上传纪念照片和感言
- **情侣模式** — 可配置纪念日计数器，在页面右上角显示在一起的天数
- **IP 限频** — 基于 IP 地址的频率限制（非昵称），防止恶意刷便签，可配置每小时最大投稿数
- **敏感词过滤** — 支持自定义敏感词黑名单，投稿和润色均会拦截
- **数据导入/导出** — 支持 JSON 格式的便签和类型数据导入导出
- **后台管理** — Console 端卡片式设置界面，便签管理和类型管理支持 sticky 标题栏，弹窗统一圆角毛玻璃风格，支持批量删除
- **权限控制** — 基于 Halo RBAC 体系，区分查看权限和管理权限
- **主题适配** — 提供 WishFinder（`@Finder("wishFinder")`）和 Thymeleaf 模板变量，支持 `th:each` 服务端渲染，主题可自定义 `wishes.html` 覆盖，插件未安装时优雅降级显示提示

## 数据模型

### Wish（便签）

| 字段 | 说明 |
|------|------|
| `content` | 便签内容 |
| `nickname` | 昵称 |
| `type` | 类型标识（wish / treehole / 自定义） |
| `color` | 卡片颜色（pink / blue / yellow / green / purple / orange） |
| `status` | 状态（pending / approved / doing / done / pending_review / rejected） |
| `anonymous` | 是否匿名 |
| `aiReply` | AI 暖心回复 |
| `emotionTag` | AI 情绪标签 emoji |
| `doneImage` | 达成纪念照片 URL |
| `doneNote` | 达成感言 |
| `priority` | 优先级（normal / important / urgent） |

### WishType（便签类型）

| 字段 | 说明 |
|------|------|
| `slug` | 类型标识 |
| `displayName` | 显示名称 |
| `description` | 描述 |
| `builtIn` | 是否内置（内置不可删除） |
| `priority` | 排序权重，越小越靠前 |

## API 接口

### 公开接口（匿名可访问）

基础路径：`/apis/anonymous.wishboard.aobp.cn/v1alpha1`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/wishes/-/submit` | 访客投稿便签 |
| POST | `/wishes/-/polish` | AI 润色内容 |

> 便签列表、类型列表、设置、AI 状态等数据已通过服务端渲染注入模板，无需前端 fetch。

### 管理接口（需登录 + 权限）

基础路径：`/apis/console.api.wishboard.aobp.cn/v1alpha1`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/wishes` | 获取全部便签 |
| GET | `/wishes/pending` | 获取待审核便签 |
| POST | `/wishes/{name}/approve` | 审核通过 |
| POST | `/wishes/{name}/reject` | 审核拒绝 |
| PUT | `/wishes/{name}` | 更新便签 |
| DELETE | `/wishes/{name}` | 删除便签 |
| POST | `/wishes/-/batch-delete` | 批量删除便签 |
| GET | `/wish-types` | 获取所有类型 |
| POST | `/wish-types` | 创建类型 |
| PUT | `/wish-types/{name}` | 更新类型 |
| DELETE | `/wish-types/{name}` | 删除类型 |
| GET | `/wish-types/-/stats` | 各类型便签数量统计 |
| GET | `/wishes/-/export` | 导出数据（JSON） |
| POST | `/wishes/-/import` | 导入数据 |

## 兼容性

| 插件版本 | Halo 版本 | 备注 |
|---------|----------|------|
| ≥ 1.0.47 | 2.23.0+ | 适配 `tools.jackson` 命名空间 + Reactor 3.8.3 空值防御 |
| ≤ 1.0.46 | 2.22.x 及以下 | 使用旧版 `com.fasterxml.jackson` API |

> **升级提示**：Halo 2.23 将 `SettingFetcher.get()` 返回类型从 `com.fasterxml.jackson.databind.JsonNode` 变更为 `tools.jackson.databind.JsonNode`，同时 Reactor 3.8.3 的 `cacheInvalidateIf` 不允许空完成。1.0.47 已迁移至 `getSettingValue()` 并添加 `onErrorResume` 防御。

## 环境要求

- Halo >= 2.20.0
- Java 21

## 构建

```bash
./gradlew clean build
```

构建产物位于 `build/libs/` 目录，将 JAR 文件上传至 Halo 后台插件管理即可安装。

## 许可证

[GPL-3.0](https://opensource.org/licenses/GPL-3.0)

---

## 主题集成（Finder API）

在主题模板中使用 `wishFinder` 获取数据：

```html
<!-- 获取所有已审核通过的便签 -->
<th:block th:with="wishes=${wishFinder.listApproved()}">
    <div th:if="${not #lists.isEmpty(wishes)}" th:each="wish : ${wishes}">
        <p th:text="${wish.spec.content}">便签内容</p>
        <span th:text="${wish.spec.anonymous ? '匿名' : wish.spec.nickname}">昵称</span>
        <span th:text="${wish.spec.color}">颜色</span>
        <span th:text="${#temporals.format(wish.spec.createdAt, 'yyyy/M/d')}">日期</span>
    </div>
</th:block>

<!-- 按类型获取便签（如只获取心愿） -->
<th:block th:with="wishes=${wishFinder.listByType('wish')}">
    <div th:each="wish : ${wishes}">
        <p th:text="${wish.spec.content}">心愿内容</p>
        <span th:if="${wish.spec.status == 'pending'}">心愿中</span>
        <span th:if="${wish.spec.status == 'doing'}">进行中</span>
        <span th:if="${wish.spec.status == 'done'}">已达成</span>
    </div>
</th:block>

<!-- 按类型获取便签（如只获取树洞） -->
<th:block th:with="wishes=${wishFinder.listByType('treehole')}">
    <div th:each="wish : ${wishes}">
        <p th:text="${wish.spec.content}">树洞内容</p>
        <div th:if="${wish.spec.aiReply}" th:text="${wish.spec.aiReply}">AI 暖心回复</div>
        <span th:if="${wish.spec.emotionTag}" th:text="${wish.spec.emotionTag}">😊</span>
    </div>
</th:block>

<!-- 获取所有便签类型 -->
<th:block th:with="types=${wishFinder.listTypes()}">
    <div th:each="type : ${types}">
        <span th:text="${type.spec.displayName}">类型名称</span>
        <span th:text="${type.spec.slug}">类型标识</span>
    </div>
</th:block>

<!-- 获取统计数据 -->
<span th:text="${wishFinder.countApproved()}">0</span> 条便签
<span th:text="${wishFinder.countByType('wish')}">0</span> 条心愿
<span th:text="${wishFinder.countByType('treehole')}">0</span> 条树洞

<!-- 获取类型映射（slug → displayName） -->
<th:block th:with="typeMap=${wishFinder.getTypeMap()}">
    <span th:text="${typeMap['wish']}">心愿</span>
    <span th:text="${typeMap['treehole']}">树洞</span>
</th:block>
```

### 便签页面模板覆盖

在主题 `templates/` 目录下创建 `wishes.html`，插件会自动使用主题模板替代内置模板。`/wishes` 页面额外注入以下模板变量：

| 变量 | 类型 | 说明 |
|------|------|------|
| `pageTitle` | `String` | 页面标题 |
| `pageSubtitle` | `String` | 页面副标题 |
| `showDaysCounter` | `boolean` | 是否显示纪念日计数器 |
| `anniversaryDate` | `String` | 纪念日日期 |
| `partnerNameA` | `String` | 伴侣名称 A |
| `partnerNameB` | `String` | 伴侣名称 B |
| `maxContentLength` | `int` | 投稿内容最大字数 |
| `enableSubmit` | `boolean` | 是否允许前端投稿（关闭后隐藏输入框，API 返回 403） |
| `aiEnabled` | `boolean` | AI 功能是否启用 |

> 便签数据和类型数据通过 `wishFinder` Finder API 获取，不再由路由注入模板变量。

## REST API

### 投稿便签

`POST /apis/anonymous.wishboard.aobp.cn/v1alpha1/wishes/-/submit`

请求体：

```json
{
  "content": "希望明年能去看极光",
  "nickname": "星辰",
  "type": "wish",
  "color": "blue",
  "anonymous": false
}
```

参数说明：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `content` | `String` | 是 | 便签内容 |
| `nickname` | `String` | 否 | 昵称，默认"匿名"，最长 20 字 |
| `type` | `String` | 否 | 类型标识，默认 `treehole`，可选 `wish` / `treehole` / 自定义 slug |
| `color` | `String` | 否 | 卡片颜色，默认 `green`，可选 `pink` / `blue` / `yellow` / `green` / `purple` / `orange` |
| `anonymous` | `boolean` | 否 | 是否匿名，默认 `false` |

响应示例（审核通过）：

```json
{
  "message": "发布成功",
  "status": "approved",
  "wish": {
    "spec": {
      "content": "希望明年能去看极光",
      "nickname": "星辰",
      "type": "wish",
      "color": "blue",
      "status": "approved",
      "anonymous": false,
      "aiReply": "愿你在极光下许下更多美好的愿望 🌌",
      "emotionTag": "✨",
      "priority": "normal",
      "createdAt": "2026-03-12T08:30:00Z"
    },
    "metadata": {
      "name": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    }
  }
}
```

响应示例（待审核）：

```json
{
  "message": "已提交，等待审核",
  "status": "pending_review",
  "wish": { ... }
}
```

### AI 润色

`POST /apis/anonymous.wishboard.aobp.cn/v1alpha1/wishes/-/polish`

请求体：

```json
{
  "content": "想去看极光",
  "nickname": "星辰"
}
```

响应示例：

```json
{
  "polished": "愿有一天，在北极的星空下，与极光共舞 🌌"
}
```

> AI 未启用时返回原文。

### Wish 数据结构

模板中通过 `wish.spec.xxx` 访问：

| 字段 | 类型 | 说明 |
|------|------|------|
| `content` | `String` | 便签内容 |
| `nickname` | `String` | 昵称 |
| `type` | `String` | 类型标识（`wish` / `treehole` / 自定义） |
| `color` | `String` | 卡片颜色（`pink` / `blue` / `yellow` / `green` / `purple` / `orange`） |
| `status` | `String` | 状态：`pending` / `approved` / `doing` / `done` / `pending_review` / `rejected` |
| `anonymous` | `boolean` | 是否匿名 |
| `aiReply` | `String` | AI 暖心回复（可能为空） |
| `emotionTag` | `String` | AI 情绪标签 emoji（可能为空） |
| `doneImage` | `String` | 达成纪念照片 URL（可能为空） |
| `doneNote` | `String` | 达成感言（可能为空） |
| `priority` | `String` | 优先级（`normal` / `important` / `urgent`） |
| `createdAt` | `Instant` | 创建时间 |
| `completedAt` | `Instant` | 完成时间（可能为空） |

### WishType 数据结构

模板中通过 `type.spec.xxx` 访问：

| 字段 | 类型 | 说明 |
|------|------|------|
| `slug` | `String` | 类型标识，如 `wish`、`treehole` |
| `displayName` | `String` | 显示名称，如"心愿"、"树洞" |
| `description` | `String` | 描述 |
| `builtIn` | `boolean` | 是否内置类型（内置不可删除） |
| `priority` | `int` | 排序权重，越小越靠前 |
