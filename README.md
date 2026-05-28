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

常用文档：

- [系统设计说明](docs/architecture.md)
- [API 接口设计草案](docs/api-draft.md)
- [手动测试与答辩演示流程](docs/manual-test-guide.md)
- [开发、部署与排错说明](docs/development-and-ops.md)
- [质量改进与后续路线](docs/quality-and-roadmap.md)
- [答辩讲稿](docs/defense-speech.md)

## 当前主链路

```text
MovieLens CSV
  -> MySQL movie_recommender
  -> Python FastAPI 算法服务
  -> Spring Boot 后端
  -> Vue 前端：登录用户、浏览电影、提交/修改评分、查看推荐、进行 LLM 问答
```

算法服务采用方案 B：直接读取 MySQL `ratings` 表进行推荐计算。推荐结果是确定性排序：同一用户、同一批评分数据和同一推荐数量下，结果会保持稳定；提交新评分后，系统会重新计算该用户推荐。

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
LLM_PROVIDER=deepseek
LLM_BASE_URL=https://api.deepseek.com
LLM_MODEL=deepseek-v4-flash
LLM_THINKING_ENABLED=false
LLM_REASONING_EFFORT=high
LLM_EXPLAIN_RECOMMENDATIONS=true
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

## 前端当前功能

- 用户注册和登录。
- 自动保存当前用户，不需要手动输入 `userId`。
- 查看个性化推荐列表。
- 搜索电影、分页浏览电影库、跳转指定页码。
- 电影库支持输入时智能候选、A-Z 首字母筛选和类型筛选。
- 点击电影可查看详情：类型、年份、平均评分、评分人数、我的评分、评分分布和相似电影。
- 电影详情内可直接评分，并可标记为“想看”“收藏”或“不感兴趣”。
- 推荐结果支持“不感兴趣”反馈，标记后当前列表会移除该电影，后续推荐会过滤该电影。
- 新增“我的画像”菜单，展示评分数量、平均评分、类型偏好、最近评分和显式偏好列表。
- 选择电影后提交评分。
- 查看“我的评分”列表，支持统计摘要、排序筛选、页内快速改分和删除评分。
- 用户评分范围为 `0.5` 到 `5.0`，步长为 `0.5`。
- 推荐结果里的 `score` 是算法排序分，不等同于用户评分满分 5 分。
- 提交评分后会立即保存；推荐结果和 DeepSeek 推荐理由在后台异步刷新，旧推荐会先保留展示。
- 推荐结果会展示结构化推荐依据，并保留 DeepSeek 生成的自然语言推荐总结。
- 推荐结果会展示可信度标签，例如类型匹配、历史评分充分、高口碑、LLM 总结和算法预测。
- 通过 LLM 问答入口询问推荐理由或观影建议，支持问题模板、上下文说明和本次问答历史。
- 前端会显示 DeepSeek 当前状态：已启用或离线模式。
- 未配置 LLM 密钥时使用离线降级回答，推荐主链路仍可运行。

## 手动测试

算法服务健康检查：

```powershell
curl.exe http://127.0.0.1:8000/api/python/health
```

后端推荐接口：

```powershell
curl.exe "http://127.0.0.1:8080/api/recommend/movie?userId=1&topN=5"
```

电影分页接口：

```powershell
curl.exe "http://127.0.0.1:8080/api/movie/page?keyword=Matrix&page=1&pageSize=12"
```

电影候选和筛选接口：

```powershell
curl.exe "http://127.0.0.1:8080/api/movie/suggest?keyword=Matrix&limit=5"
curl.exe "http://127.0.0.1:8080/api/movie/genres"
curl.exe "http://127.0.0.1:8080/api/movie/page?initial=A&genre=Comedy&page=1&pageSize=12"
```

电影详情接口：

```powershell
curl.exe "http://127.0.0.1:8080/api/movie/detail?movieId=2571&userId=1"
```

标记想看/收藏/不感兴趣：

```powershell
$body = @{
  userId = 1
  movieId = 2571
  status = "WANT"
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri "http://127.0.0.1:8080/api/movie/preference" `
  -Method Post `
  -ContentType "application/json" `
  -Body $body

curl.exe "http://127.0.0.1:8080/api/movie/preference?userId=1"
```

LLM 问答接口建议用 PowerShell：

先查看 DeepSeek 配置状态：

```powershell
curl.exe "http://127.0.0.1:8080/api/llm/status"
```

```powershell
$body = @{
  userId = 1
  queryText = "推荐一部烧脑科幻片"
  topN = 5
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri "http://127.0.0.1:8080/api/llm/query" `
  -Method Post `
  -ContentType "application/json; charset=utf-8" `
  -Body $body
```

提交评分：

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

查询当前用户评分：

```powershell
curl.exe "http://127.0.0.1:8080/api/rating/user?userId=1"
```

删除当前用户某部电影评分：

```powershell
curl.exe -X DELETE "http://127.0.0.1:8080/api/rating?userId=1&movieId=1"
```

后台推荐刷新：

```powershell
curl.exe -X POST "http://127.0.0.1:8080/api/recommend/refresh?userId=1&topN=5"
curl.exe "http://127.0.0.1:8080/api/recommend/refresh/status?userId=1"
```

评分边界说明：

- 合法：`0.5`、`1.0`、`3.5`、`5.0`
- 非法：`0.25`、`0`、`3.3`、`5.5`

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
- 电影详情、评分分布和相似电影接口。
- 想看/收藏/不感兴趣显式反馈接口。
- 评分提交接口。
- 我的评分列表接口。
- 评分删除接口。
- 非法评分边界会被拒绝。
- 提交评分后推荐接口仍可用。
- DeepSeek/LLM 状态接口。
- LLM 问答接口。

期望：

```text
Total: 18, Passed: 18, Failed: 0
```

## 启用 DeepSeek

在 `backend/.env` 中修改：

```env
LLM_ENABLED=true
LLM_PROVIDER=deepseek
LLM_BASE_URL=https://api.deepseek.com
LLM_MODEL=deepseek-v4-flash
LLM_THINKING_ENABLED=false
LLM_REASONING_EFFORT=high
LLM_EXPLAIN_RECOMMENDATIONS=true
LLM_API_KEY=你的DeepSeek API Key
```

说明：

- `deepseek-v4-flash` 用于普通问答，响应更轻量。
- 如果要使用思考模式，可改为 `LLM_MODEL=deepseek-v4-pro` 并设置 `LLM_THINKING_ENABLED=true`。
- `LLM_EXPLAIN_RECOMMENDATIONS=true` 表示推荐结果页会调用 DeepSeek 生成推荐理由。
- 推荐结果页会按照本次 `topN` 推荐数量尽量逐条生成 DeepSeek 理由；如果某一条调用失败，会自动退回算法解释。
- `deepseek-chat` 和 `deepseek-reasoner` 是兼容旧模型名，不建议新项目继续作为默认配置。

然后重启后端：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\stop-local-fullstack.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\start-local-fullstack.ps1
```

不要把真实 `LLM_API_KEY` 提交到 GitHub。

## 注意事项

- `backend/.env` 是本地环境配置，不要提交。
- `data/movielens/` 是本地数据集，不提交到仓库。
- `import-movielens-mysql.ps1` 会重置本地 MovieLens 表和测试用户，适合开发环境，不适合正式生产环境直接使用。
- 当前用户系统是课程 MVP 版本，密码仍是明文保存，后续正式化需要加入密码哈希、Token/session 和权限控制。

## 工程改进方向

当前版本以课程演示和完整链路为目标，后续可从以下方向继续工程化：

- 用户安全：密码哈希、Token/session、接口权限控制。
- 数据库治理：迁移脚本、索引优化、备份恢复流程。
- 推荐评估：增加 RMSE、MAE、Precision@K、Recall@K 等离线评估脚本。
- 代码结构：前端 `App.vue` 可继续拆成推荐、电影库、评分、画像、问答等组件。
- 部署可靠性：服务器环境变量、日志轮转、端口占用排查和一键恢复说明。

## 数据可视化

前端已经增加“数据看板”菜单，使用 ECharts 展示项目数据：

- 系统总览：电影总数、用户总数、评分总数、平均评分。
- 电影类型分布：统计 `movies.genres` 中不同类型的电影数量。
- 热门电影 Top10：按评分数量展示 MovieLens 中最常被评价的电影。
- 我的评分画像：展示当前登录用户的评分分布和类型偏好。
- 推荐结果画像：展示当前用户推荐列表的类型分布和推荐分排行。

对应后端接口：

```text
GET /api/stats/overview
GET /api/stats/genres
GET /api/stats/top-rated?limit=10
GET /api/stats/user?userId=1
GET /api/stats/recommendation?userId=1
```

“我的评分”已经从评分栏下方拆出为独立菜单页；电影库页面只负责搜索、浏览和提交评分，信息层级更清晰。
