---
name: MediStock Pro 医院进销存系统
description: 医院药品/耗材/试剂进销存的现代浅色精致 admin——单一医疗蓝 accent、冷灰地面、白面板、柔和分层阴影、150ms 微动效、等宽数字。
colors:
  accent: "#2563EB"
  accent-hover: "#1D4ED8"
  accent-deep: "#1E40AF"
  accent-ink: "#1E3A8A"
  accent-soft: "rgba(37, 99, 235, 0.08)"
  accent-hover-bg: "rgba(37, 99, 235, 0.04)"
  app-ground: "#F3F4F6"
  surface: "#FFFFFF"
  ink: "#111827"
  text-secondary: "#374151"
  text-subtitle: "#4B5563"
  text-meta: "#6B7280"
  neutral-bar: "#D1D5DB"
  border-hairline: "rgba(17, 24, 39, 0.06)"
  border-control: "rgba(17, 24, 39, 0.09)"
  danger: "#DC2626"
  warning: "#D97706"
  warning-pill-bg: "#FEF3E2"
  warning-pill-text: "#B45309"
typography:
  display:
    fontFamily: "-apple-system, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Noto Sans CJK SC', sans-serif"
    fontSize: "38px"
    fontWeight: 600
    lineHeight: 1.25
    letterSpacing: "-0.02em"
  page-title:
    fontSize: "16px"
    fontWeight: 600
    letterSpacing: "-0.01em"
  panel-title:
    fontSize: "15px"
    fontWeight: 600
    letterSpacing: "-0.01em"
  body:
    fontSize: "13px"
    fontWeight: 400
  label:
    fontSize: "13px"
    fontWeight: 500
  meta:
    fontSize: "12px"
    fontWeight: 400
  metric:
    fontSize: "26px"
    fontWeight: 600
    letterSpacing: "-0.02em"
    fontFeature: "tabular-nums"
rounded:
  xs: "4px"
  control: "6px"
  panel: "8px"
  panel-lg: "10px"
  overlay: "12px"
  card-form: "16px"
  pill: "9999px"
spacing:
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "20px"
  xxl: "24px"
components:
  panel:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.panel-lg}"
    padding: "20px 24px"
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "#FFFFFF"
    rounded: "{rounded.control}"
  button-primary-light:
    backgroundColor: "rgba(37, 99, 235, 0.1)"
    textColor: "{colors.accent}"
    rounded: "{rounded.control}"
  nav-item:
    textColor: "{colors.text-secondary}"
    rounded: "{rounded.control}"
    height: "36px"
    typography: "{typography.body}"
  nav-item-selected:
    backgroundColor: "{colors.accent-soft}"
    textColor: "{colors.accent}"
    rounded: "{rounded.control}"
    height: "36px"
  metric-value:
    textColor: "{colors.ink}"
    typography: "{typography.metric}"
---

<!-- 来源: 2026-09-24 医疗蓝整体翻新 (M13 Phase 2) 完成后从构建产物提取。
     方向: 现代浅色精致风 modern-light (Linear / 飞书), 医疗蓝 #2563EB 系, 平衡密度, 微动效——由用户问卷选定,
     取代 Phase 1 的医疗深青 #0E7C90 canon (seed 92bcdf03)。
     验证状态: vue-tsc + vite build PASS; 登录页/仪表盘/库存查询浏览器截图核验通过。
     工具备注: cursor-ide-browser 的 browser_fill 会损坏 Semi 输入框 (残留字面 "undefined"); 本栈自动化用 browser_type。
     2026-09-24 documenter 复核: 阴影四级 (--semi-shadow-0..3) 与 150ms 动效令牌移至 .impeccable/design.json extensions
     (DESIGN.md frontmatter 仅承载 Stitch schema 的 colors/typography/rounded/spacing/components)。 -->

# Design System: MediStock Pro 医院进销存系统

## Overview

**Creative North Star: "类目标准 (The Category Standard)"**

一套现代浅色、精致、桌面端的企业管理后台，服务于医院库房管理员、科室护士与采购员的长时间高频操作。设计目标不是装饰性表达，而是类目标准级的工艺：精准的间距节奏、表格数字排版、一致的状态语义色、柔和的分层阴影与克制微动效。视觉权威来自 PRODUCT.md 的品牌承诺——工艺标杆为 Linear + 飞书管理后台 + Stripe Dashboard 的交集水准。

系统为纯浅色世界：冷灰地面 (#F3F4F6) 上浮白面板，面板以发丝边框 (rgba(17,24,39,.06)) 加一级柔和阴影 (shadow-0) 浮起，hover 时阴影升至 shadow-1。全站只有一个强调色——医疗蓝 #2563EB；语义色（amber / red）只在非零风险上出现。全局 150ms 微动效让一切状态切换平滑可感。

**Key Characteristics:**
- 单一 accent：医疗蓝 #2563EB，全站锁定，无第二强调色
- 柔和分层阴影：面板静止 shadow-0、hover shadow-1、弹层 shadow-2，零生硬投影
- 150ms 微动效：颜色/背景/边框/阴影/变换/透明度全局平滑过渡
- 数据一律等宽数字 (tabular-nums)，指标值 26px/600 紧缩字距
- 语义色即风险：只有非零的风险计数才着 amber/red，零态保持中性
- 桌面端专属 Operate 模式，中文排版，系统字体栈，无断点

## Colors

调色板是 Tailwind 冷灰中性阶 (gray) 加一支医疗蓝 accent (blue)；语义色仅承担风险信号。

### Primary
- **医疗蓝 Medical Blue** (#2563EB): 唯一强调色。主按钮 (hover #1D4ED8)、深色侧栏选中导航 (rgba(59,130,246,.26) 柔光底 + 白字)、可点指标 hover (rgba(37,99,235,.04))、链接化文字、入库趋势柱、输入聚焦环 (2px rgba(37,99,235,.12))、焦点环 (rgba(37,99,235,.5))、选区 (rgba(37,99,235,.16))、表格行 hover (rgba(37,99,235,.04))、加载 spinner。Semi `--semi-blue-*` 整阶在 `body` 上被覆盖为 Tailwind blue 10 级 ramp (#EFF6FF → #172554)。
- **深墨蓝轨道 Sider Ink** (linear-gradient(180deg, #0E1C40 → #0A142C)): 侧栏专用深色轨道，与浅色内容画布形成 Stripe Dashboard 式对比。轨道上文字：主文字 rgba(226,232,240,.78)、组标题 rgba(148,163,184,.9)、弱化 rgba(226,232,240,.45~.55)、分隔边 rgba(255,255,255,.08)。
- **登录展演地面 Login Canvas** (linear-gradient(160deg, #EEF3FC → #DDE7F9) + 两束径向柔光): 仅登录页左侧展演区底色，叠加左上 90% 白径向高光与右下 rgba(147,197,253,.35) 蓝晕，不出现在管理界面正文。
- **品牌藏青 Brand Navy** (#16295E): 仅登录页品牌字标与功能卡标题 (#1E2A52) 的深色文字，不出现在管理界面正文。
- **深蓝暗部 Accent Ink** (#1E3A8A / #1D4ED8): 仅用于品牌标记渐变 (#3B82F6 → #2563EB, 135deg) 与登录提交钮渐变 (#3B82F6 → #2563EB → #1D4ED8)，不出现在管理界面正文。

### Neutral
- **墨 Ink** (#111827): 一级文字——页题、面板标题、指标值、表格正文。grey-9。
- **次级文字 Secondary** (#374151): 列表项文字、表单标签、用户名。grey-7。
- **副题 Subtitle** (#4B5563): 导航组标题、表单行内标签。grey-6。
- **元信息 Meta** (#6B7280): 12px 辅助文字——面板元信息、指标标签、图例、空态。grey-5，也是对比度下限。
- **中性条 Neutral Bar** (#D1D5DB): 出库趋势柱、三联指标分隔符、图例。grey-3。
- **应用地面 App Ground** (#F3F4F6): 主内容区底色。grey-1。
- **面板 Surface** (#FFFFFF): 面板、顶栏、表格卡。
- **发丝边框 Hairline** (rgba(17,24,39,.06)): 面板、页面卡、顶栏分隔边；深色侧栏上用 rgba(255,255,255,.08)。
- **控件边框 Control Border** (rgba(17,24,39,.09)): 输入框、按钮等控件描边 (`--semi-color-border`)，比面板发丝略深一档。
- **填充层 Fill** (rgba(31,41,55,.04/.08/.12)): hover 底色、骨架屏、用户芯片底。

### Semantic
- **风险红 Danger** (#DC2626): 非零近效期/危险计数、危险操作按钮 (theme="light" type="danger")、未读角标。
- **预警琥珀 Warning** (#D97706): 非零低库存计数。待办计数胶囊用其深色变体 #B45309 配 #FEF3E2 底。

### Named Rules
**The One Accent Rule.** 全站只有 #2563EB 一支强调色。新页面不得引入第二支 accent；需要层次时用其中性阶或 accent 的透明度变体 (.04/.08/.1/.12/.16)。

**The Risk-Only Color Rule.** 语义色 (amber #D97706 / red #DC2626) 只绑定非零风险：`:class="{ warn: count > 0 }"`。零态、正常态、空态一律中性灰。彩色不是装饰，是警报。

## Typography

**Display/Body Font:** 系统栈 `-apple-system, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Noto Sans CJK SC', sans-serif`（同一栈承担所有角色，无独立展示字体）。

**Character:** 中文优先的系统排版，靠字重 (400/500/600) 与字号阶梯分层，不靠字体对比。标题统一带 -0.01em 紧缩，大数字带 -0.02em。

### Hierarchy
- **Display** (700, 40px, -0.02em): 仅登录页品牌字标 "MediStock Pro"，品牌藏青 #16295E 纯色。
- **Page Title** (600, 16px, -0.01em): 顶栏页题，全站页面唯一一级标题。
- **Panel Title** (600, 15px, -0.01em): 面板标题、表格卡标题。
- **Body** (400, 13px): 正文、表格、导航项、按钮。
- **Label** (500, 13px): 表单标签（置于输入框上方）、导航组标题。
- **Meta** (400, 12px, #6B7280): 面板元信息、指标标签、图例、时间戳。
- **Metric** (600, 26px, -0.02em, tabular-nums): 指标条数值；三联复合值降为 19px。

### Named Rules
**The Tabular Numerals Rule.** 一切数据数字——表格、指标值、计数胶囊、趋势轴——必须 `font-variant-numeric: tabular-nums`（已在全局对 `.semi-table`、`.metric-value`、`.todo-count` 等强制）。等宽数字是账实一致感的排版基础。

## Layout

桌面端固定壳层，无响应式断点（desktop-only 契约，移动端采集被明确拒绝）。

- **应用壳**: 左 240px 深墨蓝侧栏（渐变蓝品牌标记 8px 圆角带蓝色辉光 + "MediStock Pro / 医院进销存系统" 锁up + 品牌行右侧 Indent 图标折叠钮 + 分组 Semi Nav + 底部版本署名）+ 60px 白色顶栏（16px 页题 / 260px 搜索框 / 未读铃 / 全圆 pill 用户芯片含渐变头像 / 退出）；内容区冷灰地面 #F3F4F6 叠加顶部极淡品牌蓝径向晕 (rgba(37,99,235,.06))，让白面板浮起。
- **内容区**: 地面 #F3F4F6，padding 24px，纵向 gap 20px。
- **页面骨架**: 指标条（一格面板，发丝 `border-left` 分隔）→ 待办 + 趋势 (5fr/7fr 双栏，gap 20) → 快捷入口。该骨架在仪表盘与库存总览两页复用，是系统级模式而非单页编排。
- **表格页**: 白面板卡 (padding 16px) 内 toolbar（筛选 + 主操作，gap 8–12px）+ `size="small"` 表格 + 分页。
- **侧栏树页** (库存总览): 左 220px 白面板树 + 右侧主区，gap 16px。
- **登录页**: 全屏左右结构（V3.0 原型）——左侧视觉区 (flex ~1.05)：实景背景 + 深蓝遮罩、M 字标与 slogan、中央 HUD 五模块环 (采购/库存/效期/报表/科室领用)、底部波浪与「用科技守护每一份医疗资源」；右侧表单区 (flex ~0.95, #F8FAFC 地面)：右上「安全·专业·可信赖」、白卡片 (400px)：「登录」+ 用户名/密码/验证码 (前端 4 位可刷新) + 记住账号/忘记密码 + 「登录 →」渐变钮 + v3.0 版权；不含指纹/扫码/微信等其它登录方式。

## Elevation & Depth

纵深由四层表达：冷灰地面 (#F3F4F6) → 白面板 (#FFF) + 发丝边框 (rgba(17,24,39,.06)) → 柔和阴影阶梯 → 弹层。阴影体系四级，低透明度、大模糊半径、向下偏移：

- **shadow-0** (面板静止): `0 1px 2px rgba(0,0,0,.03), 0 1px 6px -1px rgba(0,0,0,.02), 0 2px 4px rgba(0,0,0,.02)`
- **shadow-1** (面板 hover): `0 4px 6px -1px rgba(0,0,0,.05), 0 2px 4px -2px rgba(0,0,0,.03)`
- **shadow-2** (弹层/Toast): `0 10px 15px -3px rgba(0,0,0,.08), 0 4px 6px -4px rgba(0,0,0,.05)`
- **shadow-3** (保留，重弹层): `0 20px 25px -5px rgba(0,0,0,.1), 0 8px 10px -6px rgba(0,0,0,.04)`

四级阴影以 `--semi-shadow-0..3` 令牌在 `body` 上锁定，Semi Card/面板默认 shadow-0、hover 升 shadow-1；Modal/Popover/Tooltip/Dropdown 弹层 12px 圆角 + shadow-2；Toast 10px 圆角 + shadow-2。登录功能卡用 `0 4px 16px rgba(22,41,94,.06)` 极浅浮起，品牌标记与提交钮用蓝色辉光 (rgba(37,99,235,.3~.45)) 表达层级。

交互层的"深度"是色彩加微阴影：hover 用 rgba(37,99,235,.04) 或 rgba(31,41,55,.04) 底色，焦点用 2px rgba(37,99,235,.5) 外描边 (offset 1px)，输入聚焦环为 2px rgba(37,99,235,.12) 外晕。

### Named Rules
**The Soft-Shadow Rule.** 阴影只许取自四级令牌，禁止自定义生硬投影；静止面板 shadow-0，浮起反馈靠阴影升级 + 150ms 过渡，不用位移。

**The Micro-Motion Rule.** 全局 `* { transition: color, background-color, border-color, box-shadow, transform, opacity 150ms cubic-bezier(0.4,0,0.2,1) }`。状态切换必须平滑；不得引入超过 150ms 的过渡或位移动画。

## Shapes

圆角按容器层级分配：控件 6px（按钮、输入框、导航项、可点指标），面板 8–10px（表格卡 8、仪表盘面板 10、品牌标记 8），弹层 12px（Modal/Popover/Tooltip/Dropdown），登录页功能卡 14px、登录控件与提示条 8–10px、品牌标记 10–18px 按尺寸递增；4px 仅作库内部 extra-small 与趋势柱顶端圆角。计数胶囊、用户芯片与头像为全圆 (9999px / 50%)。

边框一律 1px 发丝：面板/卡片 rgba(17,24,39,.06)，控件描边 rgba(17,24,39,.09)，深色轨道上 rgba(255,255,255,.08)。导航选中态用蓝色柔光底 + 白字，不用左边条。

## Components

### Buttons
- **Shape:** 控件圆角 (6px)。
- **Primary:** 实心医疗蓝底 #2563EB + 白字 (`theme="solid" type="primary"`)，hover 加深至 #1D4ED8，用于查询、提交、审批通过等主操作。
- **Light Primary:** 浅蓝底 rgba(37,99,235,.1) + 蓝字 (`theme="light" type="primary"`)，hover 底升至 .16，用于新建类次主操作与快捷入口。
- **Danger:** `theme="light" type="danger"`，仅用于取消/删除等破坏性操作。
- **Borderless Icon:** 顶栏铃与退出 (`theme="borderless"` IconButton)。

### Navigation
- 深墨蓝轨道上的分组 Nav：项高 36px、13px、圆角 6px；项文字 rgba(226,232,240,.78)，组标题 rgba(148,163,184,.9)/500；选中 = rgba(59,130,246,.26) 柔光底 + 白字 600 + 顶部 inset 高光；hover = rgba(255,255,255,.06)；子项选中时组标题不留底色 (#F1F5F9)；折叠钮在品牌行右侧 (Indent 图标, hover rgba(255,255,255,.1))。

### Cards / Panels
- **Corner Style:** 面板圆角 8–10px。
- **Background:** 白 (#FFF)。
- **Border:** 1px 发丝 rgba(17,24,39,.06)。
- **Internal Padding:** 仪表盘面板 20px 24px；表格卡 16px；侧栏树块 12px。
- **Shadow Strategy:** 静止 shadow-0，hover shadow-1（见 The Soft-Shadow Rule）。

### Metrics Strip (signature)
全站签名组件：一格白面板内 `repeat(N, 1fr)` 栅格，指标间以发丝 `border-left` 分隔（首项无）。标签 12px #6B7280，数值 26px/600/-0.02em tabular-nums #111827；非零风险着 .warn (#D97706) / .danger (#DC2626)。可点指标 hover 铺 rgba(37,99,235,.04)，点击跳转对应流水页——每个数字可回溯。

### Inputs / Fields
- **Style:** Semi 默认控件，1px 边框 rgba(17,24,39,.09)，控件圆角 6px；标签置于输入框上方 (13px/500 #374151)。
- **Focus:** 边框转 #2563EB + 2px rgba(37,99,235,.12) 外晕；全站 `:focus-visible` 2px rgba(37,99,235,.5) 描边，offset 1px。

### Tables
Semi Table `size="small"`，数字列继承全局 tabular-nums；行 hover rgba(37,99,235,.04)。空态居中、48px 纵向留白、#6B7280。

### Status Tag Vocabulary（全站统一，跨页面同状态必同色）
- **grey 惰性**：DRAFT 草稿、CANCELLED 已取消、FROZEN 冻结、CLOSED 已关闭、IGNORED 已忽略、TERMINATED 已终止。
- **blue 待行动**（accent 承担注意力）：PENDING 待审批/待确认、PICKING 拣货中、COUNTING 盘点中、REGISTERED 已登记、INFO 级提醒。
- **violet 在途**：SHIPPED 已发运、RECEIVING 收货中、ORDERED 已下单待到货。
- **green 完成/通过**：APPROVED 已审批、CONFIRMED 已确认、RECEIVED 已收货、COMPLETED 已完成、PASSED/ACCEPTED/SIGNED、HANDLED 已处理、EFFECTIVE 生效中。
- **orange 预警**：低库存、WARN 级预警、30 天内效期。
- **red 风险**：已过期、REJECTED 已拒绝、URGENT 紧急、OPEN 未处理预警、REVERSED 已红冲（红冲即会计红字，红色是语义本色）。
- 效期阶梯单列：已过期 red / 30 天 orange / 60 天 yellow / 90 天 blue——单一阶梯通道，不与其他状态混用。

### Todo Pill
待办计数胶囊：12px/600 tabular-nums，#B45309 字 + #FEF3E2 底，全圆角；零态降级为 #6B7280 字 + rgba(31,41,55,.04) 底。待办行 hover 时文字转 #2563EB。

### Trend Bars
近 7 日出入库：入库柱 #2563EB、出库柱 #D1D5DB，宽 18px、顶端 4px 圆角；图例 10px 色块 (3px 圆角) + 12px meta 文字。

### Modal / Overlay
Semi Modal（弹层圆角 12px + shadow-2），宽 820–860px 承载单据表单与明细行栅格；底部操作区主操作 solid primary、破坏性操作 light danger。Toast 10px 圆角 + shadow-2。

### Loading / Empty / Skeleton
加载 spinner 用主色 #2563EB；骨架屏块用填充色 rgba(31,41,55,.08)，与地面同族不突兀；空态 48px 留白 + meta 色文字。

### Login Split Showcase
全屏左右结构（参照原型图实现）。左侧展演区 (flex 1)：#EEF3FC→#DDE7F9 160deg 浅蓝渐变 + 左上白色径向高光 + 右下 rgba(147,197,253,.35) 蓝晕；垂直居中品牌块——64px 渐变品牌标记 (18px 圆角 + rgba(37,99,235,.35) 辉光) → 40px/700 藏青字标 #16295E → 15px 副题 #5B6B8C (0.08em 字距)；其下 2×2 功能卡栅格 (280px/列, gap 16)：半透明白卡 (rgba(255,255,255,.65) + blur(8px) + rgba(255,255,255,.85) 边 + 14px 圆角 + 0 4px 16px rgba(22,41,94,.06))，卡内 40px 图标芯片 (#E3EBFD 底 + #2563EB 图标: Clock/Shield/Refresh/Bell) + 14px/600 标题 #1E2A52 + 12px 描述 #64748B——FEFO 先到期先出、资质过期拦截、流水幂等回放、库存动态预警；底部 12px 版权 #8A97B1。右侧表单区 (560px 白色面板)：顶部小锁up (36px 标记 + 15px 藏青字标 + 11px 平台副题)，垂直居中表单块 (max-width 400px)：26px/600 "欢迎登录" + 13px 副题 → 46px 填充式输入框 (#F3F6FC 底无边，聚焦白底 + #3B82F6 边 + 3px rgba(59,130,246,.15) 环，IconUser/IconLock 前缀 #9AA7BD) → 记住账号 (Checkbox, localStorage 持久化用户名) + 忘记密码 (Toast 引导联系管理员) → 46px 渐变提交钮 (#3B82F6→#2563EB→#1D4ED8, 0.35em 字距, 蓝色辉光) → 演示账号提示条 (#EFF6FF 底 + #DBEAFE 边 + #2563EB 12px)；底部 12px 价值署名 #9AA7BD。品牌块与表单块以 rise keyframes 分级延迟 (0/.1s) 入场；prefers-reduced-motion 关闭。

## Do's and Don'ts

### Do:
- **Do** 从 Tailwind 冷灰阶取文字色：一级 #111827、次级 #374151、副题 #4B5563、meta #6B7280——这是全站 22 个视图文件统一清扫后的既定阶。
- **Do** 让指标可点并跳转流水来源页，hover 铺 rgba(37,99,235,.04)（可追溯即信任）。
- **Do** 复用指标条/面板/表格卡骨架开新页面；库存总览已证明该模式可移植。
- **Do** 给一切数据数字加 tabular-nums。
- **Do** 用 Semi Tag 的 green/orange/red/grey 表达状态，保持四色语义不变。
- **Do** 从 `--semi-shadow-0..3` 取阴影；hover 升级一级，配合 150ms 过渡。

### Don't:
- **Don't** 引入第二支强调色或彩色条纹统计卡（库存总览的彩条卡片已被修复为指标条面板）。
- **Don't** 使用 off-palette 红 #D32F2F；危险色锁定 #DC2626。
- **Don't** 让辅助文字浅于 #6B7280。
- **Don't** 给导航选中态加左边条；选中态用蓝色柔光底 + 白字。
- **Don't** 为零值/正常态着语义色；彩色只给非零风险。
- **Don't** 引入超过 150ms 的过渡、位移动画或自定义阴影。
- **Don't** 添加移动端断点或触屏布局；本系统为桌面端 Operate 模式契约。
