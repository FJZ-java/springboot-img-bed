# 火星图床 (Mars Image Bed)

基于 Spring Boot 3 + Vue 3 的开箱即用图床服务，图片存储在 GitHub 仓库并通过 jsDelivr CDN 全球加速。

![Java](https://img.shields.io/badge/Java-17+-orange) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen) ![Vue](https://img.shields.io/badge/Vue-3.x-42b883) ![SQLite](https://img.shields.io/badge/SQLite-3-blue)

## ✨ 功能特性

### 核心能力
- **图片上传**：拖拽 / 多选批量上传，单次最多 9 张，自动返回 jsDelivr CDN 直链
- **多仓库存储**：支持配置多个 GitHub 仓库，上传随机分发，自动统计仓库容量与文件数
- **游客体验**：未登录游客可直接上传试用（不产生记录），可配置开关与限流
- **上传记录**：表格化管理所有上传，支持预览、复制链接、Markdown 引用、批量删除
- **分享链接**：为单张图片生成公开分享页 `/share/{code}`，附 Markdown / HTML / BBCode 引用格式

### 用户与权限
- **注册 / 登录**：JWT 令牌认证，密码 BCrypt 加密存储
- **角色分级**：管理员可访问仓库管理、存储配置、用户管理、轮播管理、数据统计
- **用户封禁**：封禁后无法登录，已签发的令牌立即失效
- **API Key**：每个用户拥有独立 API Key，可通过 `X-API-Key` 请求头调用所有需登录的接口

### 管理后台
- **数据统计**：核心数据卡片（图片数 / 占用空间 / 用户数 / 分享数等）+ ECharts 可视化（上传趋势、仓库容量分布、用户贡献排行、账号状态、图片格式分布）
- **轮播管理**：自定义广告轮播图（上传或外链），展示在上传页，支持排序 / 启停 / 跳转链接
- **在线背景**：登录弹窗与后台页面壁纸背景

## 🔐 接口安全

- 图片上传三层校验：扩展名白名单 → 文件魔数（magic number）比对 → SVG 脚本检测（防存储型 XSS）
- 接口限流：登录防爆破（5 次失败锁 10 分钟）、注册 / 上传 / 公开接口滑动窗口限流
- 安全响应头：`X-Content-Type-Options` / `X-Frame-Options` / `Referrer-Policy` 等
- 敏感密钥（GitHub Token）通过 `application-local.yml`（gitignore）或环境变量注入，不入库
- 轮播图 URL 协议白名单（仅 http/https，拒绝 `javascript:` 伪协议）

## 🛠️ 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3、MyBatis-Plus、SQLite、JWT (jjwt)、Spring Interceptor |
| 前端 | Vue 3、Vite、Naive UI、Pinia、Vue Router、ECharts、Axios |
| 存储 | GitHub Contents API + jsDelivr CDN |

## 🚀 快速开始

### 环境要求
- JDK 17+、Maven 3.6+
- Node.js 18+

### 1. 配置密钥

复制 `backend/application-local.example.yml` 为 `backend/application-local.yml`，填入你的 GitHub Token（需要目标仓库的读写权限）：

```yaml
imgbed:
  github:
    token: YOUR_GITHUB_TOKEN_HERE
```

> 该文件已被 `.gitignore` 忽略，不会提交。也可以用环境变量 `GITHUB_TOKEN` 代替。

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端运行在 `http://localhost:8080`，首次启动自动建表、创建管理员账号并播种默认仓库。

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端运行在 `http://localhost:5173`，已配置代理转发至后端。

### 4. 登录使用

浏览器打开 `http://localhost:5173`，用管理员账号登录即可。管理员账号 / 密码通过环境变量 `ADMIN_USERNAME` / `ADMIN_PASSWORD` 配置。

## 🔑 API 调用示例

在网页「API」页面查看 / 重置你的 API Key，然后：

```bash
# 上传图片
curl -X POST http://localhost:8080/api/records/upload \
  -H "X-API-Key: img_sk_xxxxxxxx" \
  -F "file=@photo.png"

# 查询上传记录
curl "http://localhost:8080/api/records?page=1&size=10" \
  -H "X-API-Key: img_sk_xxxxxxxx"

# 删除记录（同时删除 GitHub 上的文件）
curl -X DELETE http://localhost:8080/api/records/{id} \
  -H "X-API-Key: img_sk_xxxxxxxx"
```

## 📁 项目结构

```
├── backend/                  # Spring Boot 后端
│   └── src/main/java/com/fjz/imgbed/
│       ├── auth/             # JWT、拦截器、用户上下文
│       ├── carousel/         # 轮播广告管理
│       ├── common/           # 统一返回、全局异常
│       ├── config/           # Web 配置、Schema 迁移
│       ├── guest/            # 游客上传（限流）
│       ├── github/           # GitHub Contents API 客户端
│       ├── record/           # 上传记录
│       ├── security/         # 限流器、安全响应头
│       ├── share/            # 分享链接
│       ├── stats/            # 管理端统计
│       ├── storage/          # 多仓库存储配置
│       ├── upload/           # 图片上传服务（魔数校验）
│       └── user/             # 用户、管理员、API Key
└── frontend/                 # Vue 3 前端
    └── src/
        ├── components/       # AuthModal、AdCarousel、ShareModal 等
        ├── layouts/          # 主布局（动态菜单、壁纸背景）
        ├── router/           # 路由（管理员守卫）
        ├── utils/            # echarts 按需引入、分享格式
        └── views/            # 首页 / 上传 / 记录 / API / 统计 / 用户 / 轮播 / 仓库 / 分享页
```

## ⚠️ 注意事项

- GitHub Token 属于敏感信息，务必只放在 `application-local.yml` 或环境变量中，**不要提交到仓库**；一旦泄露请立即到 GitHub → Settings → Developer settings 吊销
- jsDelivr CDN 对中国大陆访问稳定性有限，生产环境可替换为自有对象存储
- SQLite 适合个人 / 小团队使用，数据文件位于 `backend/data/imgbed.db`，请定期备份
