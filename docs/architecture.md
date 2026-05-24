# 系统设计说明

## 1. 总体架构

系统采用前后端分离和分层解耦架构，整体分为应用层、服务层、算法层和数据层。

```text
应用层：Vue / Vite 前端
  -> RESTful API
服务层：Spring Boot 后端
  -> HTTP JSON
算法层：Python FastAPI 推荐算法服务
  -> SQL
数据层：MySQL movie_recommender
```

LLM 作为外部语义增强服务，由后端统一调用，不直接参与推荐排序。未配置 LLM 密钥时，后端返回离线默认解释，保证推荐主链路可运行。

## 2. 模块划分

| 模块 | 目录 | 职责 |
|---|---|---|
| 前端应用 | `frontend/` | 用户入口、推荐展示、电影分页浏览、评分提交、LLM 问答 |
| 后端服务 | `backend/` | API 服务、业务编排、数据库访问、算法服务调用、LLM 降级 |
| 算法服务 | `algorithm-service/` | 读取 MySQL 评分数据，计算推荐结果 |
| 数据存储 | MySQL | 存储用户、电影、评分和推荐缓存 |
| 部署脚本 | `deploy/scripts/` | 下载数据、导入数据、启动、停止和测试 |
| 项目文档 | `docs/` | 需求、架构、接口和阶段总结 |

## 3. 应用层设计

前端采用 Vue 3 与 Vite 构建，当前页面包含：

- 用户注册和登录入口。
- 当前用户状态展示。
- 推荐数量 topN 输入。
- 推荐列表展示。
- 电影搜索、分页浏览和每页数量选择。
- 点选电影后提交评分。
- 提交评分后刷新推荐结果。
- LLM 问答入口，用于解释推荐结果或回答观影问题。

本地开发时，Vite 通过代理将：

```text
/api -> http://127.0.0.1:8080
```

因此前端代码只请求 `/api/...`，避免浏览器跨域问题。

## 4. 服务层设计

后端采用 Spring Boot 实现，主要分层如下：

```text
backend/src/main/java/com/whu/movie/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
└── config/
```

| 层次 | 职责 |
|---|---|
| controller | 接收 HTTP 请求，提供 RESTful API |
| service | 处理推荐、评分、用户、电影浏览和 LLM 查询业务 |
| repository | 访问 MySQL 数据库 |
| entity | 映射数据库表 |
| dto | 定义请求和响应对象 |
| config | 公共配置 |

核心服务：

| 类名 | 职责 |
|---|---|
| `RecommendationService` | 调用算法服务，保存并读取推荐结果 |
| `RatingService` | 保存评分并触发推荐刷新 |
| `UserService` | 处理课程 MVP 级注册和登录 |
| `MovieService` | 提供电影搜索和分页浏览数据 |
| `PythonAlgorithmClient` | 调用 Python 算法服务 |
| `LlmClient` | 调用外部 LLM 或返回离线默认解释 |

## 5. 算法层设计

算法层采用 Python FastAPI 实现，当前直接读取 MySQL：

```sql
SELECT user_id, movie_id, score AS rating
FROM ratings
WHERE score IS NOT NULL;
```

推荐流程：

1. 从 MySQL `ratings` 表读取 MovieLens 评分。
2. 构造用户-电影评分矩阵。
3. 对已有用户使用相似用户偏好和 SVD 隐语义特征生成推荐。
4. 对冷启动用户使用高评分热门电影兜底。
5. 返回 `movieId`、`score`、`reason` 给后端。

## 6. 数据库设计

数据库名：

```text
movie_recommender
```

核心表：

| 表名 | 说明 |
|---|---|
| `users` | 用户信息 |
| `movies` | MovieLens 电影信息 |
| `ratings` | 用户评分，算法服务的主要输入 |
| `recommendations` | 推荐缓存和推荐理由 |

MovieLens 数据不提交到 Git 仓库，其他电脑运行项目时需要先下载并导入数据。

## 7. 核心业务流程

### 7.1 推荐流程

```text
前端请求推荐
  -> 后端 RecommendationController
  -> RecommendationService 检查推荐缓存
  -> 缓存不足时调用 PythonAlgorithmClient
  -> Python 算法服务读取 MySQL ratings 并计算推荐
  -> 后端根据 movieId 查询 movies 表
  -> 后端保存 recommendations 并返回前端
```

### 7.2 评分流程

```text
前端选择电影并提交评分
  -> 后端 RatingController
  -> RatingService 保存 ratings
  -> 触发 RecommendationService 刷新该用户推荐
  -> 前端重新请求推荐列表
```

### 7.3 用户与电影浏览流程

```text
用户注册或登录
  -> 后端返回 userId 和 username
  -> 前端将当前用户保存到 localStorage
  -> 前端调用 /api/movie/page 分页浏览电影
  -> 用户点选电影后提交评分
```

### 7.4 LLM 问答流程

```text
前端提交自然语言问题
  -> 后端 LlmController
  -> LlmService 获取当前用户推荐结果
  -> LlmClient 调用外部 LLM 或返回离线降级回答
  -> 前端展示回答和相关电影
```

## 8. 运行与测试设计

本地脚本：

| 脚本 | 说明 |
|---|---|
| `download-movielens-small.ps1` | 下载 MovieLens small |
| `import-movielens-mysql.ps1` | 导入 MovieLens 到 MySQL |
| `start-local-fullstack.ps1` | 启动算法服务和后端 |
| `stop-local-fullstack.ps1` | 停止本地服务 |
| `test-local-fullstack.ps1` | 验证 MySQL、算法、后端、评分、用户、电影分页和 LLM 问答链路 |

前端独立启动：

```powershell
cd frontend
npm run dev
```

## 9. 当前实现状态

已完成：

- MovieLens small 导入 MySQL。
- 算法服务直接读取 MySQL `ratings`。
- 后端推荐接口返回真实电影标题和类型。
- 评分提交后刷新推荐缓存。
- 用户注册/登录接口。
- 电影搜索与分页浏览接口。
- 前端推荐列表、电影浏览、评分提交和 LLM 问答联调。
- 本地测试脚本验证多用户推荐稳定性和分页接口。

待完善：

- 用户密码哈希、Token/session 和权限控制。
- 部署环境下的数据备份和恢复流程。
- LLM 真实密钥配置后的效果验证。
