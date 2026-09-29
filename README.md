# MediStock-Pro

> 用科技守护每一份医疗资源

面向医院及医疗机构的**进销存一体化管理系统**，覆盖 采购 → 入库 → 库存 → 申领 → 核算 全链路，将物资主数据、批次效期、采购、科室申领、审批流与报表分析纳入统一平台。

![License](https://img.shields.io/badge/license-学习免费%20%7C%20商业授权-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.5-6DB33F)
![Vue](https://img.shields.io/badge/Vue-3.5-42B883)
![Java](https://img.shields.io/badge/Java-17-orange)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1)

---

## 核心特性

- **全院多级组织**：集团 / 医院 / 院区 / 科室四级组织树，多仓库库位管理，库存数据按仓库行级隔离
- **批次效期精细化**：库存精确到批次，FEFO 先效期先出自动推荐；近效期 / 已过期分级预警，问题批次可冻结封存
- **业务全链路闭环**：采购申请 → 订单 → 到货 → 验收 → 入库；申领 → 审批 → 锁定 → 拣货 → 复核 → 签收 → 红冲；调拨、盘点、退库、报废全程留痕
- **合规审批留痕**：申领、采购、调拨、报废五类单据统一审批中心，关键操作全部记入操作日志
- **精细化权限**：75 个权限点 + 15 菜单分组 RBAC，叠加全部数据 / 按仓库 / 本机构三级数据范围，权限精确到按钮
- **管理驾驶舱**：库存总值、可用 / 锁定 / 在途结构、近 7 日出入库趋势、待办审批一屏汇总

**工程规模**：后端 37 个 Controller、204 个 Java 类、53 张表（Flyway），前端 28 个业务页面，内置完整演示数据，开箱即用。

---

## 功能模块

| 模块 | 核心能力 |
| --- | --- |
| 工作台 | 仪表盘指标、待办聚合、审批中心、消息通知、单据中心、全局搜索 |
| 库存管理 | 库存总览（分类树+库房树联动）、批次级查询、入库 / 出库 / 调拨 / 盘点 / 退库 / 报废、效期预警 |
| 采购管理 | 采购申请 → 订单 → 到货 → 验收 → 入库全链路；采购协议、供应商档案与报价 |
| 科室申领 | 组织树选科室 → 审批 → 锁定 → 拣货 → 签收，紧急 / 普通优先级 |
| 报表中心 | 收发存汇总、供应商分析 |
| 系统管理 | RBAC 权限 + 数据权限、组织机构、物资主数据、操作日志、系统参数、数据字典、CSV 导入导出、接口监控 |

---

## 技术栈

| 层 | 技术 |
| --- | --- |
| 前端 | Vue 3.5 + TypeScript 5.6 + Vite 5 + Pinia + Vue Router 4 |
| UI | Semi Design（@kousum/semi-ui-vue）+ ECharts 5 |
| 桌面端 | Electron |
| 后端 | Spring Boot 3.3.5 + Java 17 |
| ORM | MyBatis-Plus 3.5.9 |
| 鉴权 | Sa-Token 1.44 |
| 数据库 | MySQL 8.0 + Flyway |
| 文档 | Knife4j / OpenAPI 3 |
| 构建 | Maven + npm |

工程特性：Sa-Token 鉴权、乐观锁并发控制、幂等键防重复提交、Flyway 自动建表 + 演示数据、TypeScript 全量类型检查。

---

## 项目结构

```
MediStockPro/
├── MediStockPro-web/       # 前端 (Vue 3 + Vite)
│   └── src/{api,constants,directives,layout,router,stores,views}
├── MediStockPro-server/    # 后端 (Spring Boot)
│   └── src/main/java/com/medistock/pro/{common,config,modules}
├── MediStockPro-desktop/   # Electron 桌面端
└── docs/                   # 截图 + 宣传页
```

---

## 快速开始

**环境要求**：JDK 17+ / Node.js 18+ / MySQL 8.0+ / Maven 3.8+

```sql
-- 1. 建库（表结构与演示数据由 Flyway 自动执行）
CREATE DATABASE medistock_pro DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

```bash
# 2. 启动后端（默认 dev profile，零配置）
cd MediStockPro-server
mvn spring-boot:run    # → http://localhost:8081，接口文档 http://localhost:8081/doc.html

# 3. 启动前端
cd MediStockPro-web
npm install
npm run dev            # → http://localhost:5173

# 4. 桌面端（可选，需先启动后端与前端）
cd MediStockPro-desktop
npm install && npm run dev
```

**默认账号**：`admin` / `admin123`（全部权限）· `pharma` / `123456`（零权限，验证权限隔离）

---

## 环境配置与打包

### 前端（Vite modes）

| 文件 | 命令 | 产物 | API 地址 |
| --- | --- | --- | --- |
| `.env.development` | `npm run dev` | — | `/api/v1` 经代理转发 |
| `.env.development` | `npm run build:dev` | `dist-dev/` | 同上，带 sourcemap |
| `.env.production` | `npm run build` | `dist/` | `/api/v1` 同源，Nginx 反代 |
| `.env.electron` | `npm run build:electron` | `dist/` | `http://localhost:8081/api/v1` |

Web 生产部署（`medistockpro.coderoadmap.cn`）Nginx 参考：

```nginx
server {
    listen 80;
    server_name medistockpro.coderoadmap.cn;
    root /opt/medistock-pro/web;
    location / { try_files $uri $uri/ /index.html; }
    location /api/ {
        proxy_pass http://127.0.0.1:8081/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

### 后端（Spring Profiles）

配置拆分：`application.yml`（公共）+ `application-dev.yml`（开发，内置默认值）+ `application-prod.yml`（生产，全部环境变量注入无默认值）。

```bash
cd MediStockPro-server
mvn clean package -DskipTests    # 产出 target/medistock-pro-server.jar

# 生产启动（必须注入环境变量，缺失即失败）
SPRING_PROFILES_ACTIVE=prod \
MYSQL_HOST=数据库主机 MYSQL_USERNAME=账号 MYSQL_PASSWORD=强密码 JWT_SECRET_KEY=随机密钥 \
java -jar target/medistock-pro-server.jar
```

### 桌面端打包

```bash
cd MediStockPro-desktop
npm run build         # 前端 build:electron + Windows zip
npm run build:nsis    # NSIS 安装包
```

### 一键打包

```bash
build-prod.bat    # 前端生产包 + 后端 jar + 桌面端 → dist-prod/
```

---

## 许可证

**「学习免费 · 商业授权」** 双轨许可，详见 [LICENSE](LICENSE)：

- ✅ 个人学习、研究、技术评估、教学演示：免费使用，保留版权声明
- ⚠️ 任何商业用途须事先取得书面授权（生产部署、二次销售、SaaS、商业交付等）

商业授权 / 合作请联系 [coderoadmap.cn](https://coderoadmap.cn/about) 团队：**bingfeng_li@163.com**（微信：`byoungfeng`）

---

## 更新迭代计划

本项目由 [coderoadmap.cn](https://coderoadmap.cn/about) 团队持续开发与维护。

### v3.1 — 体验优化与稳定性加固（2026 Q4）

| 模块 | 更新内容 | 技术方案 | 验收标准 |
| --- | --- | --- | --- |
| 前端性能 | 首屏加载优化，路由级代码分割 | Vite manualChunks + `import()` 懒加载 | Lighthouse ≥ 85，LCP < 2.5s |
| 表格交互 | 列设置、筛选器保存、行内编辑 | Semi Table Pro 封装 + localStorage | 筛选条件刷新不丢失 |
| 消息通知 | WebSocket 实时推送，替代轮询 | Spring WebSocket + STOMP | 状态变更 2s 内到达 |
| 批量操作 | 入库/出库/盘点批量录入 | 虚拟滚动表格 + 批量校验接口 | 500 行 ≤ 3s |
| 移动端适配 | 申领审批、库存查询适配手机 | Semi 响应式断点 + 移动端路由 | 375px 无横向滚动 |
| 后端健壮性 | 全局异常体系，入参校验注解化 | @Valid + 统一错误码 | 无堆栈泄露 |

### v3.2 — 业务扩展与数据赋能（2027 Q1）

| 模块 | 更新内容 | 技术方案 | 验收标准 |
| --- | --- | --- | --- |
| 库存预警引擎 | 低库存补货建议、呆滞分析 | 定时任务扫描阈值 → 补货建议单 | 准确率 ≥ 80% |
| 报表增强 | 趋势图、周转率、采购价格走势 | ECharts 自定义图表 + 聚合查询 | 多维筛选 |
| 供应商评价 | 交货准时率、合格率、价格评分 | 自动统计 + 加权评分 | 权重可配置 |
| 多租户 | 组织树升级租户隔离，支持 SaaS | MyBatis-Plus 租户拦截器 | 数据完全隔离 |
| 接口开放平台 | OpenAPI Schema + API 限流 | Knife4j 导出 + 限流 | 100 QPS/租户 |
| 桌面端离线 | 弱网可查看库存 | Electron SQLite 缓存 + 增量同步 | 断网可查 50 条单据 |

### v3.3 — 架构升级与智能化（2027 Q2+）

| 模块 | 更新内容 | 技术方案 | 验收标准 |
| --- | --- | --- | --- |
| 前端架构 | Vue 3.6+，Suspense 数据预取 | lazy 组件 + 路由级 prefetch | 路由切换无白屏 |
| 后端可观测 | Actuator + Prometheus + Grafana | 指标暴露 + 仪表盘 | 实时监控 |
| AI 辅助决策 | 采购量预测、效期消耗分析 | Prophet / LightGBM 时序模型 | MAPE ≤ 15% |
| 容器化部署 | Docker Compose 一键部署 | 多阶段 Dockerfile + compose | 5 分钟完成 |
| 审计日志增强 | 结构化存储 + 日志检索 | Elasticsearch | 查询 ≤ 100ms，90 天回溯 |
| 小程序端 | 申领与审批微信小程序 | uni-app 跨端编译 | 消息模板推送待办 |

### 质量标准

- **代码**：vue-tsc 零错误，SonarQube 零 Critical
- **性能**：列表查询 ≤ 800ms，单据提交 ≤ 500ms，50 并发无超时
- **兼容**：Chrome/Edge/Firefox/Safari 最新两版，Windows 10+ / macOS 12+
- **安全**：无注入 / XSS / 越权，依赖漏洞扫描零 High/Critical

> 路线图根据开发进度与社区反馈动态调整，欢迎在 [Issues](https://github.com/byoungfeng/MediStock-Pro/issues) 提交建议。

---

## 免责声明

本项目为企业级进销存系统的学习与参考实现，不保证完全符合各地区的医疗监管要求。用于真实医疗业务前，请自行完成合规评估与专业验收。
