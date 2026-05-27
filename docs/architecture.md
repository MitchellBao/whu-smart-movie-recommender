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

LLM 作为外部语义增强服务，由后端统一调用，不直接参与推荐排序。当前默认接入 DeepSeek 的 OpenAI-compatible API，默认模型为 `deepseek-v4-flash`。未配置 LLM 密钥时，后端返回离线默认解释，保证推荐主链路可运行。

## 2. 模块划分

| 模块 | 目录 | 职责 |
|---|---|---|
| 前端应用 | `frontend/` | 用户入口、推荐展示、电影分页浏览、电影详情、评分提交与修改、用户画像、LLM 问答 |
| 后端服务 | `backend/` | API 服务、业务编排、数据库访问、显式偏好反馈、算法服务调用、LLM 降级 |
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
- 电影搜索、分页浏览、跳转指定页码和每页数量选择。
- 电影库支持输入时智能候选、A-Z 首字母筛选和类型筛选。
- 点击电影可查看详情，包括类型、年份、平均评分、评分人数、评分分布、当前用户评分状态、显式偏好状态和相似电影。
- 电影详情内可直接评分，也可标记想看、收藏或不感兴趣。
- 推荐卡片支持“不感兴趣”，用于把该电影从当前推荐中移除，并作为后续推荐过滤条件。
- “我的画像”视图展示评分数量、平均评分、类型偏好、最近评分和显式偏好列表。
- 点选电影后提交评分。
- 查看“我的评分”列表，支持统计摘要、排序筛选、页内快速改分、删除评分和回填到电影库。
- 评分规则提示：最低 `0.5`，最高 `5.0`，步长 `0.5`。
- 推荐分说明：推荐结果中的 `score` 是算法排序分，不等同于用户评分满分 5 分。
- 评分保存立即返回，推荐结果和 DeepSeek 推荐理由在后台异步刷新。
- LLM 问答入口，用于解释推荐结果或回答观影问题，并提供问题模板、上下文说明和本次问答历史。
- 推荐结果展示结构化推荐依据和可信度标签，并保留 DeepSeek 生成的自然语言总结。
- DeepSeek 状态显示，用于提示当前是已启用还是离线模式。

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
| `RatingService` | 校验并保存/更新/删除评分，查询用户评分列表，支撑推荐更新 |
| `UserService` | 处理课程 MVP 级注册和登录 |
| `MovieService` | 提供电影搜索和分页浏览数据 |
| `MoviePreferenceService` | 保存和查询想看、收藏、不感兴趣等显式偏好反馈 |
| `PythonAlgorithmClient` | 调用 Python 算法服务 |
| `LlmClient` | 调用 DeepSeek 或返回离线默认解释 |

DeepSeek 配置说明：

- 默认接口地址：`https://api.deepseek.com`
- 默认模型：`deepseek-v4-flash`
- 如需思考模式，可使用 `deepseek-v4-pro` 并设置 `LLM_THINKING_ENABLED=true`
- 推荐结果页可以调用 DeepSeek 生成推荐理由，解释条数跟随本次 `topN` 推荐数量；单条调用失败时退回算法解释，保证推荐列表仍可展示
- API Key 只允许写入本地 `backend/.env` 或服务器环境变量，不提交到 Git 仓库

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

当前算法是确定性排序算法。同一用户、同一批评分数据和同一推荐数量下，结果会保持稳定。只有用户新增评分、数据集变化或推荐数量变化时，展示结果才会变化。

推荐结果中的 `score` 是算法排序分，其计算口径是 SVD 隐向量预测值与电影历史平均评分的综合结果，因此可能大于 5。它用于排序，不等同于用户评分。

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
| `user_movie_preferences` | 用户显式偏好反馈，包括想看、收藏和不感兴趣 |

评分规则：

- 用户评分最低为 `0.5`。
- 用户评分最高为 `5.0`。
- 用户评分步长为 `0.5`。
- 后端会拒绝 `0.25`、`3.3`、`5.5` 等非法分数。

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
  -> RatingService 校验评分范围和 0.5 步长
  -> RatingService 新增或更新 ratings
  -> 触发 RecommendationService 刷新该用户推荐
  -> 前端重新请求推荐列表
  -> 前端刷新“我的评分”列表
```

用户也可以在“我的评分”页直接修改或删除评分。修改评分复用 `/api/rating/submit`，删除评分调用 `DELETE /api/rating`，随后前端刷新评分画像并触发推荐后台刷新。

### 7.3 用户与电影浏览流程

```text
用户注册或登录
  -> 后端返回 userId 和 username
  -> 前端将当前用户保存到 localStorage
  -> 前端调用 /api/movie/page 分页浏览电影
  -> 用户可以搜索、上一页/下一页或跳转指定页码
  -> 用户点选电影后提交评分
  -> 用户可以在“我的评分”中查看并修改历史评分
```

### 7.4 LLM 问答流程

```text
前端提交自然语言问题
  -> 后端 LlmController
  -> LlmService 获取当前用户推荐结果
  -> LlmClient 调用 DeepSeek 或返回离线降级回答
  -> 前端展示回答和相关电影
```

### 7.5 显式偏好反馈流程

```text
用户在详情页或推荐卡片点击想看/收藏/不感兴趣
  -> 后端 MoviePreferenceController
  -> MoviePreferenceService 新增或更新 user_movie_preferences
  -> 推荐读取时过滤 DISLIKE 电影
  -> 前端刷新用户画像与推荐列表
```

## 8. 运行与测试设计

本地脚本：

| 脚本 | 说明 |
|---|---|
| `download-movielens-small.ps1` | 下载 MovieLens small |
| `import-movielens-mysql.ps1` | 导入 MovieLens 到 MySQL |
| `start-local-fullstack.ps1` | 启动算法服务和后端 |
| `stop-local-fullstack.ps1` | 停止本地服务 |
| `test-local-fullstack.ps1` | 验证 MySQL、算法、后端、评分边界、用户、电影分页和 LLM 问答链路 |

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
- 用户评分边界和 0.5 步长校验。
- 评分提交后新增或更新评分，并通过后台异步任务刷新推荐缓存。
- 我的评分列表与修改回填。
- 我的评分统计摘要、排序筛选、快速改分和删除评分。
- 用户注册/登录接口。
- 电影搜索、分页浏览和指定页跳转。
- 电影详情接口、评分分布、用户评分状态、显式偏好状态和相似电影推荐。
- 想看、收藏、不感兴趣反馈接口。
- 推荐结构化解释字段和可信度标签，包括偏好类型、历史评分依据、电影热度/口碑、LLM 总结和算法预测分。
- 前端推荐列表、电影浏览、评分提交和 LLM 问答联调。
- DeepSeek 状态接口与前端状态显示。
- 智能问答问题模板、上下文说明和本次问答历史。
- 本地测试脚本验证多用户推荐稳定性、分页接口和评分边界。

待完善：

- 用户密码哈希、Token/session 和权限控制。
- 部署环境下的数据备份和恢复流程。
- DeepSeek 真实密钥配置后的效果验证。
## 10. 数据可视化设计

前端已经从单一主界面调整为菜单式应用，包含“推荐结果”“电影库与评分”“我的评分”“我的画像”“数据看板”“智能问答”六个主要视图。其中“我的评分”作为独立菜单页展示，不再嵌入评分栏下方。

数据看板采用 ECharts 渲染，主要包含两类可视化。

第一类是系统级数据展示：

- 系统总览指标卡：电影总数、用户总数、评分总数、平均评分。
- 电影类型分布：由 `movies.genres` 拆分统计，用于展示电影库覆盖范围。
- 热门电影 Top10：由 `ratings` 按电影聚合，用于展示 MovieLens 数据集中用户交互最集中的电影。

第二类是用户与推荐相关展示：

- 我的评分画像：由当前用户的 `ratings` 生成评分分布和类型偏好。
- 推荐结果画像：由当前用户的 `recommendations` 生成推荐类型分布和推荐分排行。

后端新增 `StatsController` 和 `StatsService`，统一提供 `/api/stats/*` 接口。数据看板不改变推荐算法，只用于解释数据规模、用户偏好和推荐结果特征。
