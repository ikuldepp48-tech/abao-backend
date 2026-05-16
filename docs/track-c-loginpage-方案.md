# LoginPage 实现方案 — 用户端登录授权页

> Track C Day 1-2 | 2026-05-16 | 待 Leven 审阅 (Step 3)

---

## A. 3 层架构定位

依据：Module 3 PRD + 蓝图 V3 第 10 章

```
L1 基础设施：微信登录 + 授权流程
  - wx.getUserProfile / wx.login 获取 code
  - POST /api/member/auth 后端授权
  - 协议勾选 + 同意/拒绝按钮

L2 懂顾客的数据采集：授权信息
  - 昵称、头像、手机号、性别、地区
  - 首次登录时间记录
  - 设备信息关联

L3 留顾客的价值传递：新人欢迎
  - 新人欢迎语（"欢迎来到阿堡"）
  - 新人福利展示（注册即送优惠券）
  - 不是空手注册 → 归属感 + 互惠
```

---

## B. 5 步生存链定位

```
主要 Step 2（达成共识 — 注册即建立关系）
  - 用户阅读并同意协议 = 建立共识
  - 微信授权 = 关系确立

次要 Step 3（获得资源 — 顾客成为可触达的数据源）
  - 授权信息上传 → 企业获得顾客数据
  - 顾客获得福利 → 互惠闭环
```

---

## C. 人性维度嵌入

| 人性维度 | 实现方式 |
|---|---|
| 归属感 | "欢迎来到阿堡" 品牌问候 + AbaoLogo 72px 大标识 |
| 互惠 | 注册即送新人优惠券（不是空手注册） |
| 安全感 | 隐私协议说明 + 最小必要原则承诺 |
| 禁止 | 强制授权弹窗（操纵） → 允许"拒绝并退出" |

---

## D. 页面结构 + 组件清单

基于 JSX 源码 (pages-2.jsx:472) + 现有 login.vue + 12 公共组件库

```
┌─ StatusBar（微信壳）               ─┐
├─ 滚动区（wx-scroll）               ─┤
│                                    │
│  [1] AbaoLogo (size=72)            │  品牌标识 — 最大尺寸
│      品牌标题 "欢迎来到阿堡"        │  font-display 44rpx 800
│      副标题 "温馨提示"              │  font-body 24rpx
│                                    │
│  [2] AbaoCard (variant=minimal)    │  隐私协议卡片
│      · 协议正文                    │  浅底 + 细边框
│      · 最小必要原则（加粗）         │
│                                    │
│  [3] 同意勾选                      │  自定义 checkbox
│      ○/● "我已阅读并同意..."       │  红色选中 + 圆形
│                                    │
│  [4] 按钮组 (grid 1:1)            │
│      Capsule(variant=ghost)        │  "拒绝并退出"
│      Capsule(variant=filled)       │  "同意并登录"
│                                    │
│  [5] 微信登录提示                  │  黄色背景卡片
│      💬 + "使用微信一键登录"       │  引导微信授权
│                                    │
├─ HomeBar（底部 indicator）         ─┘
```

**使用的公共组件**：
- AbaoLogo（已有）— size=72
- AbaoCard（新）— variant=minimal，隐私协议区
- Capsule（已有）— filled/ghost，替代原始 button

**不使用的组件**：AbaoPageHeader（登录页是特殊页，用 Logo+标题更合适）、Tape（登录页不需要胶带装饰）

**与现有 login.vue 的差异**：
1. 隐私协议区用 `<AbaoCard variant="minimal">` 替代裸 `<view>`
2. 按钮用 `<Capsule>` 替代裸 `<button>`
3. 新增 loading 状态（授权中骨架屏）
4. 新增 error 状态（授权失败 ErrorState）
5. 未勾选时 "同意并登录" 按钮用 Capsule ghost + disabled 态

---

## E. 视觉规划

### E.1 颜色规划

| 区域 | Token | 说明 |
|---|---|---|
| 页面背景 | `#fff` | 设计稿白底 |
| 标题 | `var(--ink-900)` | 最深文本 |
| 副标题 | `var(--ink-500)` | 辅助文本 |
| 协议卡片底 | `var(--ink-100)` | AbaoCard minimal |
| 协议链接文字 | `var(--abao-red)` | 可点击暗示 |
| 勾选圆圈 | `var(--abao-red)` | 选中态 |
| 主按钮 | `var(--abao-red)` + `var(--shadow-red)` | Capsule filled |
| 拒绝按钮 | `transparent` + `var(--abao-red)` border | Capsule ghost/outline |
| 微信提示底 | `var(--abao-yellow-soft)` | 黄色背景 |
| 微信提示标题 | `var(--abao-red-deep)` | 深红强调 |

### E.2 字体规划

| 文字 | 字体 | 字号 | 字重 |
|---|---|---|---|
| "欢迎来到阿堡" | `var(--font-display)` | 44rpx | 800 |
| "温馨提示" | `var(--font-body)` | 24rpx | 400 |
| 协议正文 | `var(--font-body)` | 24rpx | 400 |
| 最小必要原则 | `var(--font-body)` | 24rpx | 600 |
| 协议链接 | `var(--font-body)` | 24rpx | 400 |
| 勾选文字 | `var(--font-body)` | 22rpx | 400 |
| 按钮文字 | `var(--font-body)` | 28rpx | 600 |
| "使用微信一键登录" | `var(--font-body)` | 24rpx | 700 |
| "方便快捷·保障账号安全" | `var(--font-body)` | 20rpx | 400 |

### E.3 圆角规划

| 元素 | Token |
|---|---|
| 协议卡片 | `var(--r-md)` = 14px |
| 勾选圆圈 | `50%` |
| 按钮 | `var(--r-pill)` = 999px |
| 微信提示卡 | `var(--r-md)` = 14px |

### E.4 阴影规划

| 元素 | Token |
|---|---|
| 同意并登录按钮 | `var(--shadow-red)` |

---

## F. 数据接入说明

### F.1 微信登录流程

```
用户点击"同意并登录" 或 "微信一键登录"
  → 检查 agreed 状态（未勾选 → toast 提示）
  → 调用 sheep.$platform.useProvider('wechat').login()
    → 微信授权弹窗（用户确认）
    → 获取 code + userInfo
  → POST /api/member/auth
    → 后端验证 code
    → 创建/更新会员记录
    → 发放新人优惠券
  → 本地存储 token
  → 跳转首页 + toast "欢迎加入阿堡"
```

### F.2 授权信息字段

| 字段 | 来源 | 必填 |
|---|---|---|
| openId | wx.login code 换取 | ✅ |
| nickName | wx.getUserProfile | ✅ |
| avatarUrl | wx.getUserProfile | ✅ |
| gender | wx.getUserProfile | 可选 |
| mobile | 微信手机号组件 | 可选 |

### F.3 失败处理

| 场景 | 处理 |
|---|---|
| 微信授权被拒绝 | ErrorState(errorType="permission") + "需要授权才能使用阿堡" |
| 网络失败 | ErrorState(errorType="network") + retry 按钮 |
| 后端接口失败 | ErrorState(errorType="server") + "服务暂不可用，请稍后重试" |

---

## G. 状态管理

| 状态 | 展示 | 触发条件 |
|---|---|---|
| 默认 | 完整页面（Logo + 协议 + 按钮） | 页面加载完成 |
| 未勾选 | "同意并登录" 按钮 disabled（灰色 Capsule ghost） | agreed = false |
| 已勾选 | "同意并登录" 按钮 active（红色 Capsule filled + shadow-red） | agreed = true |
| 授权中 | LoadingSkeleton（全页骨架屏 + "正在授权..."文字） | 点击登录后，等待微信/后端 |
| 授权成功 | 3s toast "欢迎加入阿堡" → 跳转首页 | 后端返回成功 |
| 授权失败 | ErrorState（对应错误类型 + Capsule retry） | 微信拒绝/网络错误/后端错误 |

**与现有 login.vue 的差异**：
- 现有代码只有 agreed true/false 两种状态
- 新增：授权中（LoadingSkeleton）、授权失败（ErrorState）

---

## H. 交互动作

| 操作 | 触发 | 反馈 | 跳转 |
|---|---|---|---|
| 点击协议链接 | tap | 无 | `/pages/public/richtext?type=agreement` |
| 点击勾选区 | tap | 切换 agreed + 圆圈变色 | 不跳转 |
| 点击"拒绝并退出" | tap | Modal 提示 | 不跳转（小程序不能主动退出） |
| 点击"同意并登录"(未勾选) | tap | 按钮无反应 | 不跳转 |
| 点击"同意并登录"(已勾选) | tap | 进入授权中状态 | 成功→首页 |
| 点击"微信一键登录" | tap | 同上 | 同上 |
| 授权失败 | ErrorState retry | 重新走登录流程 | 不跳转 |

---

## I. 工程级要求

| 要求 | 实现 |
|---|---|
| 操作日志 | 后端 `/api/member/auth` 加 `@LogRecord`（Track A 已完成） |
| 隐私保护 | 手机号脱敏显示；日志不记录完整手机号 |
| 防作弊 | 后端检测同设备多账号（M2 实现，本次不阻塞） |
| 输入校验 | agreed 必须为 true 才允许调用授权 |
| 异常处理 | 所有 try/catch 都有用户可见的反馈（toast/ErrorState） |
| 微信登录 | 使用 sheep 平台层的 provider，不直接调 wx.login |

---

## J. 4 状态截图规划

| 截图 | 内容 | Mock 方式 |
|---|---|---|
| default | Logo + 协议 + 未勾选按钮 + 微信提示 | 正常加载 |
| loading | 点击登录后 → LoadingSkeleton | mock 慢网络 3s |
| empty | 协议内容为空（理论上不发生） | mock 后端返回空协议 |
| error | 微信授权拒绝 → ErrorState(permission) | 真机拒绝授权 |

---

## K. 文件规划

```
修改：
  pages/index/login.vue  — 改造为组件化 + 状态管理完整版

不改：
  sheep/ 平台层代码（微信登录 provider）
  components/abao/*     — 12 组件库不动
```

**预估代码量**：~180 行（现 241 行 → 精简至 180-200 行，组件复用减少重复 CSS）

---

## L. 与原设计稿（JSX）的一致性

| JSX 元素 | 实现 |
|---|---|
| AbaoLogo size=72 | ✅ 同 |
| "欢迎来到阿堡" font-display 22px 800 | → 44rpx (小程序) |
| "温馨提示" fontSize 12 color ink-500 | ✅ 同 |
| 隐私协议卡片 (bg=var(--bg) r-md) | ✅ AbaoCard minimal |
| 协议链接 color=abao-red | ✅ 同 |
| 最小必要原则加粗 | ✅ 同 |
| ✓ 圆形勾选 (18px 红底白字) | ✅ 同(36rpx) |
| "拒绝并退出" btn-ghost | ✅ Capsule ghost |
| "同意并登录" btn-primary | ✅ Capsule filled + shadow-red |
| 微信提示 (yellow-soft 底) | ✅ 同 |
| StatusBar + Capsule + HomeBar | 小程序原生，不需实现 |

---

## 结论

现有 `login.vue` 已基本还原设计稿。Track C 的改造集中在 3 点：
1. **组件化**：隐私卡片 → AbaoCard，按钮 → Capsule
2. **状态完整**：新增 loading(LoadingSkeleton) + error(ErrorState)
3. **人性嵌入明确**：归属感（欢迎语）+ 互惠（新人福利）+ 安全感（隐私承诺）

⏸ **Step 3 — 等 Leven 审阅方案后进入 Step 4 实施**
