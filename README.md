# WHU Smart Movie Recommender

面向大规模用户行为数据的智能电影推荐系统。项目来自武汉大学《计算机综合项目实践》课程，当前版本已跑通 MovieLens 数据导入、MySQL 存储、Python 算法服务、Spring Boot 后端服务和 Vue 前端初步联调。

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
  ↓
MySQL movie_recommender
  ↓
Python FastAPI 算法服务
  ↓
Spring Boot 后端
  ↓
Vue 前端登录用户、浏览电影、展示推荐结果并提交评分
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

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\start-local-fullstack.ps1 -InstallDeps
```

启动成功后：

```text
算法服务：http://127.0.0.1:8000
后端服务：http://127.0.0.1:8080
```

## 启动前端

另开一个终端：

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
/api → http://127.0.0.1:8080
```

因此页面中直接请求 `/api/recommend/movie` 和 `/api/rating/submit`，避免浏览器跨域问题。

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

Vite 前端代理推荐接口：

```powershell
curl.exe "http://127.0.0.1:5173/api/recommend/movie?userId=1&topN=5"
```

提交评分建议使用 PowerShell：

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
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\test-local-fullstack.ps1 -SkipLlmCheck
```

当前测试会检查：

- MySQL 连接
- 算法服务能读取 MovieLens `ratings`
- 推荐接口返回完整 MovieLens 电影信息
- 多个用户都能稳定返回推荐
- 评分提交接口可用
- 提交评分后推荐接口仍可用

期望：

```text
Total: 6, Passed: 6, Failed: 0
```

## 停止服务

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\stop-local-fullstack.ps1
```

## 常用接口

| 功能 | 方法 | 地址 |
|---|---|---|
| 用户注册 | POST | `http://127.0.0.1:8080/api/user/register` |
| 用户登录 | POST | `http://127.0.0.1:8080/api/user/login` |
| 搜索电影 | GET | `http://127.0.0.1:8080/api/movie/search?keyword=Matrix&limit=12` |
| 算法健康检查 | GET | `http://127.0.0.1:8000/api/python/health` |
| 获取推荐 | GET | `http://127.0.0.1:8080/api/recommend/movie?userId=1&topN=5` |
| 提交评分 | POST | `http://127.0.0.1:8080/api/rating/submit` |
| LLM 查询 | POST | `http://127.0.0.1:8080/api/llm/query` |

## 当前状态

已完成：

- MovieLens small 数据下载与导入 MySQL
- 算法服务直接读取 MySQL 评分数据
- 后端推荐接口返回真实电影标题、类型、得分和推荐理由
- 评分提交后刷新推荐缓存
- Vue 前端初步联调：注册/登录、推荐列表展示、电影搜索浏览、点选电影评分并刷新推荐
- 本地全栈测试脚本增强

当前用户模块说明：

- 注册和登录信息保存在 MySQL `users` 表。
- 前端登录后会把当前用户信息保存在浏览器 `localStorage`，刷新页面后仍能记住当前用户。
- 当前为课程 MVP 实现，密码暂未做哈希加密，也未接入 JWT / Session 权限体系；正式生产环境需要补充安全认证机制。
- 如果重新执行 `import-movielens-mysql.ps1`，会重置 `users`、`movies`、`ratings`、`recommendations`，因此本地测试用户会被清空。

下一步：

- 接入 LLM 查询界面
- 增强用户安全认证
- 整理测试截图、阶段总结和答辩材料
