# yunWMS 前端

两个应用共用一套设计令牌与基础包。

## 结构

```
code/frontend/
├── apps/
│   ├── owner-portal/       # 货主端，端口 5173，comfortable 密度
│   └── network-console/    # 仓网管理端，端口 5174，compact 密度
├── packages/
│   ├── tokens/             # 设计令牌（CSS 变量 + 两套密度）
│   ├── ui-web/             # 共用组件
│   └── request/            # Axios 封装、令牌存储、错误码
├── package.json
├── pnpm-workspace.yaml
└── tsconfig.base.json
```

## 命令

```bash
# 安装
pnpm install

# 开发（两个终端各跑一个）
pnpm dev:owner      # http://localhost:5173
pnpm dev:network    # http://localhost:5174

# 构建
pnpm build:owner
pnpm build:network

# 类型检查
pnpm typecheck
```

后端代理固定指向 `http://localhost:8080`，见各应用的 `vite.config.ts`。

## 当前状态

骨架阶段，已打通：

- 路由与登录守卫（无令牌跳登录页）
- 布局（侧栏、顶栏、内容区）、中英切换
- 设计令牌生效，两端密度不同
- 共用组件 `PageHeader`、`StatusTag`、`EmptyState`
- 请求封装、错误码映射、真实登录与本人改密
- 登录后按 `mustChangePassword` 进入改密页，并按 `accessiblePortals` 校验端

当前只有 `POST /api/auth/login` 与 `PUT /api/auth/password` 接入后端；业务页面仍是静态骨架，未消费货主、用户、角色或字典接口。

## 页面清单

| 应用 | 路由 | 状态 |
| --- | --- | --- |
| 货主端 | `/login` | 真实登录，支持强制改密 |
| | `/dashboard` | 静态示例 |
| | `/product` | 列表骨架，静态数据 |
| 仓网端 | `/login` | 真实登录，支持强制改密 |
| | `/dashboard` | 静态示例 |
| | `/customer` | 列表骨架，静态数据，未接后端 |
| | `/warehouse` | 空间树与档案骨架 |
| | `/product` | 审核队列骨架 |
| | `/system` | 六个页签占位 |

## 约定

1. 组件里不写十六进制色值，只用语义令牌，见 `packages/tokens/README.md`。
2. `packages/ui-web` 只放两端都会用的组件，单端专用组件放各自 `src/components/`。
3. 接口接入时，`src/api/` 按后端 controller 命名并保持一致；当前只有认证接口已接入。
