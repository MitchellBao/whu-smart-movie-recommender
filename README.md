# WHU Smart Movie Recommender

面向大规模用户行为数据的智能电影推荐系统。项目来自武汉大学《计算机综合项目实践》课程，当前版本已经跑通 MovieLens 数据导入、MySQL 存储、Python 推荐算法服务、Spring Boot 后端服务和 Vue 前端联调。

## 项目结构

```text
whu-smart-movie-recommender/
├── frontend/                 # Vue 3 / Vite 前端
├── backend/                  # Spring Boot 后端
├── algorithm-service/        # Python FastAPI 推荐算法服务
├── deploy/scripts/           # 下载数据、导入数据、启动、停止、测试脚本
├── docs/                     # 项目文档
└── README.md
```

## 当前主链路

```text
MovieLens CSV
  -> MySQL movie_recommender
  -> Python FastAPI 算法服务
  -> Spring Boot 后端
  -> Vue 前端：用户登录、电影浏览、推荐展示、评分提交、LLM 问答
```

算法服务采用方案 B：直接读取 MySQL `ratings` 表进行推荐计算。后端根据算法返回的 `movieId` 查询 MySQL `movies` 表，补全 `title` 和 `genres`。

## 环境准备

需要安装：

- Git
- JDK 17 或 21
- Maven
- Python 3.10+
- Node.js 20+
- MySQL 8
- VS Code PowerShell 终端

在 `backend/.env` 新建本机配置：

```env
MYSQL_URL=jdbc:mysql://127.0.0.1:3306/movie_recommender?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
MYSQL_USER=root
MYSQL_PASSWORD=你的MySQL密码

ALGORITHM_SERVICE_URL=http://127.0.0.1:8000

LLM_ENABLED=false
LLM_BASE_URL=https://api.deepseek.com/v1
LLM_MODEL=deepseek-chat
LLM_API_KEY=change_me
```

不要提交 `backend/.env`。

## 初始化 MovieLens 数据

下载 MovieLens small：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\download-movielens-small.ps1
```

导入 MySQL：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\import-movielens-mysql.ps1
```

成功后应看到：

```text
[OK] imported users:   610
[OK] imported movies:  9742
[OK] imported ratings: 100836
[OK] cleared recommendations cache
```

`data/movielens/` 已加入 `.gitignore`，数据集不会提交到 GitHub。其他电脑 clone 后需要自行下载并导入。

## 启动后端与算法服务

在项目根目录运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\start-local-fullstack.ps1 -InstallDeps
```

启动成功后：

```text
算法服务：http://127.0.0.1:8000
后端服务：http://127.0.0.1:8080
```

停止服务：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\stop-local-fullstack.ps1
```

## 启动前端

另开一个 VS Code 终端：

```powershell
cd frontend
npm install
npm run dev
```

访问：

```text
http://127.0.0.1:5173/
```

前端通过 Vite 代理访问后端：

```text
/api -> http://127.0.0.1:8080
```

因此页面中直接请求 `/api/...`，避免浏览器跨域问题。

## 前端当前功能

- 用户注册和登录。
- 自动保存当前用户，不需要手动输入 `userId`。
- 查看个性化推荐列表。
- 搜索电影或按页浏览电影库。
- 选择电影后提交评分。
- 提交评分后刷新推荐结果。
- 通过 LLM 问答入口询问推荐理由或观影建议。
- 未配置 LLM 密钥时使用离线降级回答，推荐主链路仍可运行。

## 手动测试

算法服务健康检查：

```powershell
curl.exe http://127.0.0.1:8000/api/python/health
```

期望：

```json
{"status":"ok","ratingCount":100836}
```

后端推荐接口：

```powershell
curl.exe "http://127.0.0.1:8080/api/recommend/movie?userId=1&topN=5"
```

电影分页接口：

```powershell
curl.exe "http://127.0.0.1:8080/api/movie/page?keyword=Matrix&page=1&pageSize=12"
```

LLM 问答接口建议用 PowerShell：

```powershell
$body = @{
  userId = 1
  queryText = "推荐一部烧脑科幻片"
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri "http://127.0.0.1:8080/api/llm/query" `
  -Method Post `
  -ContentType "application/json; charset=utf-8" `
  -Body $body
```

提交评分建议用 PowerShell：

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

## 一键测试

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\test-local-fullstack.ps1
```

当前测试会检查：

- MySQL 连接。
- 算法服务能读取 MovieLens `ratings`。
- 推荐接口返回完整 MovieLens 电影信息。
- 多个用户都能稳定返回推荐。
- 用户注册和登录接口。
- 电影搜索和分页接口。
- 评分提交接口。
- 提交评分后推荐接口仍可用。
- LLM 问答接口。

期望：

```text
Total: 10, Passed: 10, Failed: 0
```

## 注意事项

- `backend/.env` 是本地环境配置，不要提交。
- `data/movielens/` 是本地数据集，不提交到仓库。
- `import-movielens-mysql.ps1` 会重置本地 MovieLens 表和测试用户，适合开发环境，不适合正式生产环境直接使用。
- 当前用户系统是课程 MVP 版本，密码仍是明文保存，后续正式化需要加入密码哈希、Token/session 和权限控制。
