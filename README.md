# WHU Smart Movie Recommender

面向大规模用户行为数据的智能电影推荐系统。项目来自武汉大学《计算机综合项目实践》课程，目标是实现一个结合传统推荐算法、大语言模型解释能力和 Web 工程实践的电影推荐系统。

## 项目简介

本系统围绕“推荐什么”和“为什么推荐”两个问题展开：

- 推荐算法服务负责根据用户评分和电影信息计算 Top-N 推荐结果。
- Spring Boot 后端负责业务编排、数据库访问、算法服务调用和 LLM 推荐理由生成。
- Vue 前端负责页面展示、用户交互和推荐结果呈现。
- MySQL 负责存储用户、电影、评分和推荐解释等数据。

项目的核心设计思想是：推荐算法负责数值计算与排序，大语言模型作为语义增强层负责生成自然语言解释，不直接替代推荐算法。

## 当前仓库结构

```text
whu-smart-movie-recommender/
├── README.md
├── frontend/                 # Vue / Vite 前端
├── backend/                  # Spring Boot 后端服务
├── algorithm-service/        # Python FastAPI 推荐算法服务
├── deploy/
│   ├── scripts/              # 本地启动、停止、测试脚本
│   └── baota/                # 宝塔部署相关文件
├── docs/                     # 项目文档
│   ├── 01-topic-and-team.md
│   ├── 02-requirements-draft.md
│   ├── architecture.md
│   ├── api-draft.md
│   └── stage-summaries/
└── .github/                  # GitHub 工作流配置
```

说明：旧版 `algorithm/` 和 `database/` 目录已经不再作为当前主结构使用。当前算法服务目录是 `algorithm-service/`，数据库表结构由后端 JPA 与初始化数据共同维护。

## 技术栈

| 模块 | 技术 |
|---|---|
| 前端 | Vue 3 / Vite |
| 后端 | Spring Boot / Maven / JPA |
| 算法服务 | Python / FastAPI / Uvicorn / pandas / numpy |
| 数据库 | MySQL 8 |
| 智能解释 | OpenAI 兼容格式 LLM API，可离线降级 |
| 本地开发 | Windows / VS Code / PowerShell |

## 核心功能

- 获取个性化电影推荐结果。
- 提交用户电影评分。
- 后端调用 Python 算法服务完成推荐计算。
- 根据推荐结果生成自然语言推荐理由。
- 未配置 LLM 密钥时，系统可使用离线默认解释继续运行。
- 提供本地全栈启动、停止和测试脚本。

## 团队分工

| 成员 | 角色 | 主要职责 |
|---|---|---|
| 鲍明颉 | 组长 / Scrum Master / 系统架构负责人 | 项目统筹、总体架构设计、技术路线把控、接口规范、数据库关键字段设计、LLM 语义增强定位、文档统筹 |
| 杨舸 | 核心算法、后端与部署实现负责人 | Python 推荐算法服务、Spring Boot 后端 MVP、数据库联调、本地启动脚本、部署脚本和全栈测试链路 |
| 伍锡飞 | 前端实现与测试负责人 | Vue 前端页面、推荐结果展示、交互体验和系统测试 |

## 本地运行前准备

建议使用 VS Code 打开项目根目录：

```powershell
cd F:\Projects\whu-smart-movie-recommender
code .
```

需要提前准备：

- JDK 21
- Python 3.10 或更高版本
- Node.js 20 或更高版本
- MySQL 8
- VS Code PowerShell 终端

数据库默认配置见 [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml)：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/movie_recommender
    username: root
    password: root
```

如果你的 MySQL 密码不是 `root`，请在 `backend/.env` 或当前 PowerShell 环境中设置对应变量，不要把密码提交到 GitHub。

## 一键启动后端与算法服务

在项目根目录执行：

```powershell
.\deploy\scripts\start-local-fullstack.ps1 -InstallDeps
```

脚本会自动完成：

1. 准备 `algorithm-service/.venv` Python 虚拟环境。
2. 安装算法服务依赖。
3. 启动 Python FastAPI 算法服务。
4. 启动 Spring Boot 后端服务。
5. 等待健康检查通过。

启动成功后会看到类似输出：

```text
Algorithm health: http://127.0.0.1:8000/api/python/health
Backend API:      http://127.0.0.1:8080/api/recommend/movie?userId=1&topN=3
Stop command:     .\deploy\scripts\stop-local-fullstack.ps1
```

## 启动前端

另开一个 VS Code 终端：

```powershell
cd frontend
npm install
npm run dev
```

默认访问地址：

```text
http://localhost:5173/
```

## 手动接口测试

推荐使用 `curl.exe`，不要用 PowerShell 的 `curl` 别名。

### 1. 检查算法服务

```powershell
curl.exe http://127.0.0.1:8000/api/python/health
```

预期结果：

```json
{"status":"ok"}
```

### 2. 获取推荐结果

```powershell
curl.exe "http://127.0.0.1:8080/api/recommend/movie?userId=1&topN=3"
```

预期结果：返回 `code: 0` 和推荐电影列表。

### 3. 提交评分

PowerShell 中建议用 `Invoke-RestMethod`，避免 JSON 转义问题：

```powershell
$body = @{
  userId = 1
  movieId = 1
  score = 4.5
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri "http://127.0.0.1:8080/api/rating/submit" `
  -Method Post `
  -ContentType "application/json" `
  -Body $body
```

### 4. 测试自然语言查询

```powershell
$body = @{
  userId = 1
  queryText = "推荐一部烧脑科幻片"
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri "http://127.0.0.1:8080/api/llm/query" `
  -Method Post `
  -ContentType "application/json" `
  -Body $body
```

如果没有配置 LLM 密钥，系统会返回离线模式的默认解释。

## 一键测试

服务启动后执行：

```powershell
.\deploy\scripts\test-local-fullstack.ps1
```

该脚本主要测试：

- MySQL 是否可连接。
- Python 算法服务健康检查是否通过。
- 后端推荐接口是否能返回数据。
- 评分提交接口是否可用。
- 提交评分后推荐接口是否仍可用。
- LLM 查询接口是否能返回结果。

如果希望脚本自动尝试启动服务：

```powershell
.\deploy\scripts\test-local-fullstack.ps1 -AutoStart
```

## 停止本地服务

```powershell
.\deploy\scripts\stop-local-fullstack.ps1
```

日志位置：

```text
.local-run/logs/algorithm.log
.local-run/logs/backend.log
```

## 常用接口

| 功能 | 方法 | 地址 |
|---|---|---|
| 算法服务健康检查 | GET | `http://127.0.0.1:8000/api/python/health` |
| 获取电影推荐 | GET | `http://127.0.0.1:8080/api/recommend/movie?userId=1&topN=3` |
| 提交评分 | POST | `http://127.0.0.1:8080/api/rating/submit` |
| LLM 自然语言查询 | POST | `http://127.0.0.1:8080/api/llm/query` |

## 文档

- [选题及团队组建报告](docs/01-topic-and-team.md)
- [项目需求规格说明书](docs/02-requirements-draft.md)
- [系统设计说明书](docs/architecture.md)
- [API 草案](docs/api-draft.md)
- [第一阶段个人工作总结](docs/stage-summaries/phase1-personal-work-summary.md)

## Git 协作建议

当前 `main` 分支保存稳定版本。后续建议逐步采用：

```text
main
dev
feature/frontend
feature/backend
feature/algorithm
feature/docs
```

开发流程建议：

1. 从 `dev` 新建功能分支。
2. 功能完成后提交 Pull Request。
3. 代码审查通过后合并。
4. 阶段性可运行版本打 tag，例如 `v0.1.0-mvp`。

## 项目状态

当前版本已经合并算法服务、后端服务、部署脚本和文档整理内容，支持本地启动算法服务与后端服务，并可进行基础推荐、评分提交和 LLM 查询链路测试。前端仍保留原有 Vue 工程结构，后续可继续与后端接口联调。
