# 第三部分 系统设计说明书

## 3.1 系统总体设计

本系统采用分层、解耦的微服务架构思想进行设计。系统自底向上划分为数据层、算法层、服务层和应用层，各层职责明确，通过标准化接口进行通信，实现高内聚、低耦合，并支持项目后续扩展。

系统整体结构如下：

```text
应用层：frontend/
    ↓ RESTful API
服务层：backend/
    ↓ HTTP JSON
算法层：algorithm-service/
    ↓
数据层：MySQL
```

大语言模型作为外部语义增强服务，由后端服务层统一调用，不直接参与推荐排序。

```text
Vue 前端
  ↓
Spring Boot 后端
  ↓
Python FastAPI 算法服务

Spring Boot 后端
  ↓
MySQL 数据库

Spring Boot 后端
  ↓
外部 LLM 服务
```

## 3.2 系统模块设计

系统主要划分为以下模块：

| 模块 | 目录 | 职责 |
|---|---|---|
| 前端应用模块 | `frontend/` | 用户交互、推荐结果展示、评分入口、推荐理由展示 |
| 后端服务模块 | `backend/` | 接口服务、业务编排、数据库访问、算法服务调用、LLM 调用 |
| 算法服务模块 | `algorithm-service/` | 推荐计算、协同过滤、SVD 排序、推荐结果返回 |
| 数据存储模块 | MySQL | 用户、电影、评分、推荐结果持久化 |
| 部署联调模块 | `deploy/` | 本地启动、停止、测试和服务器部署脚本 |
| 文档模块 | `docs/` | 项目文档、接口文档、阶段总结 |

## 3.3 应用层设计

应用层采用 Vue.js 与 Vite 构建，负责系统可视化界面和用户交互。前端不直接访问算法服务，而是统一通过 Spring Boot 后端提供的 RESTful API 获取数据。

前端主要页面和交互包括：

- 推荐电影列表展示。
- 电影标题、类型、推荐得分展示。
- 推荐理由展示。
- 用户评分入口。
- 智能推荐查询入口。

## 3.4 服务层设计

服务层采用 Spring Boot 框架实现，是系统的业务中枢。

后端目录结构如下：

```text
backend/src/main/java/com/whu/movie/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
└── config/
```

各部分职责：

| 层次 | 职责 |
|---|---|
| controller | 接收前端 HTTP 请求，提供 RESTful API |
| service | 处理推荐、评分、LLM 查询等业务流程 |
| repository | 访问 MySQL 数据库 |
| entity | 映射数据库表结构 |
| dto | 定义请求和响应数据格式 |
| config | 提供公共配置和 Bean |

主要服务类包括：

| 类名 | 职责 |
|---|---|
| `RecommendationService` | 推荐业务编排，调用算法服务并保存推荐结果 |
| `RatingService` | 保存用户评分并触发推荐刷新 |
| `PythonAlgorithmClient` | 调用 Python 算法服务 |
| `LlmClient` | 调用外部 LLM 服务或返回降级文案 |
| `LlmService` | 处理自然语言推荐查询 |

## 3.5 算法层设计

算法层采用 Python FastAPI 实现，作为独立微服务运行。

算法服务目录：

```text
algorithm-service/
├── app/main.py
└── requirements.txt
```

算法服务提供两个主要接口：

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/python/health` | GET | 算法服务健康检查 |
| `/api/python/calculate` | POST | 根据用户编号计算推荐结果 |

算法设计采用两阶段思路：

1. 召回阶段：基于用户评分矩阵，利用相似用户偏好生成候选电影集合。
2. 排序阶段：使用 SVD 矩阵分解思想计算用户与候选电影的潜在匹配程度，并结合评分偏置生成最终得分。

算法服务返回结构化 JSON 数据，包括 `movieId`、`score` 和 `reason`。

## 3.6 数据库物理设计

系统采用 MySQL 作为数据层。数据库建表文件位于：

```text
backend/src/main/resources/schema.sql
```

### 3.6.1 users 表

用于存储用户基础信息。

| 字段 | 类型 | 说明 |
|---|---|---|
| user_id | INT | 用户唯一编号 |
| username | VARCHAR(50) | 用户名 |
| password | VARCHAR(255) | 密码字段 |
| gender | VARCHAR(10) | 性别 |
| age | INT | 年龄 |

### 3.6.2 movies 表

用于存储电影基础信息。

| 字段 | 类型 | 说明 |
|---|---|---|
| movie_id | INT | 电影唯一编号 |
| title | VARCHAR(255) | 电影标题 |
| release_year | INT | 上映年份 |
| genres | VARCHAR(255) | 电影类型 |
| director | VARCHAR(100) | 导演 |

### 3.6.3 ratings 表

用于存储用户评分行为。

| 字段 | 类型 | 说明 |
|---|---|---|
| rating_id | INT | 评分记录编号 |
| user_id | INT | 用户编号 |
| movie_id | INT | 电影编号 |
| score | FLOAT | 评分值 |
| timestamp | BIGINT | 评分时间戳 |

### 3.6.4 recommendations 表

用于存储推荐结果和推荐理由。

| 字段 | 类型 | 说明 |
|---|---|---|
| id | INT | 推荐记录编号 |
| user_id | INT | 目标用户 |
| movie_id | INT | 推荐电影 |
| predicted_score | FLOAT | 算法预测得分 |
| reason | TEXT | 推荐理由 |

其中 `reason` 字段是系统可解释推荐能力的关键设计，用于保存算法说明或 LLM 生成的自然语言解释。

## 3.7 核心业务流程设计

### 3.7.1 推荐生成与解释流程

```text
1. 用户在前端请求推荐列表。
2. 前端调用后端 `/api/recommend/movie` 接口。
3. 后端检查数据库中是否已有推荐缓存。
4. 若无推荐结果，后端调用 Python 算法服务。
5. 算法服务根据用户评分数据生成推荐结果。
6. 后端查询电影元数据，补充标题和类型。
7. 后端根据配置决定是否调用 LLM 生成推荐解释。
8. 后端将推荐结果和推荐理由写入 recommendations 表。
9. 后端向前端返回推荐列表。
10. 前端展示电影、得分和推荐理由。
```

### 3.7.2 用户评分流程

```text
1. 用户在前端提交评分。
2. 前端调用 `/api/rating/submit`。
3. 后端校验评分数据。
4. 后端保存评分记录到 ratings 表。
5. 后端触发推荐结果刷新。
6. 后端返回评分提交状态。
```

### 3.7.3 自然语言查询流程

```text
1. 用户输入自然语言查询。
2. 前端调用 `/api/llm/query`。
3. 后端获取用户当前推荐结果。
4. 后端组装 Prompt。
5. LLMClient 调用外部 LLM 服务。
6. 若 LLM 未启用或调用失败，返回默认降级文案。
7. 后端返回回答文本和相关电影列表。
```

## 3.8 类与接口设计

系统后端采用面向对象设计，核心类职责如下：

| 类名 | 类型 | 职责 |
|---|---|---|
| `User` | Entity | 用户实体 |
| `Movie` | Entity | 电影实体 |
| `Rating` | Entity | 评分实体 |
| `Recommendation` | Entity | 推荐结果实体 |
| `RecommendationController` | Controller | 推荐接口入口 |
| `RatingController` | Controller | 评分接口入口 |
| `LlmController` | Controller | 自然语言查询接口入口 |
| `RecommendationService` | Service | 推荐主流程编排 |
| `PythonAlgorithmClient` | Client | 调用算法服务 |
| `LlmClient` | Client | 调用大语言模型服务 |

其中 `LlmClient` 作为大语言模型调用抽象，便于切换不同 LLM 服务商，体现依赖倒置思想。

## 3.9 接口设计

### 3.9.1 前后端接口

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/recommend/movie` | GET | 获取推荐电影 |
| `/api/rating/submit` | POST | 提交用户评分 |
| `/api/llm/query` | POST | 自然语言推荐查询 |

### 3.9.2 内部服务接口

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/python/health` | GET | 算法服务健康检查 |
| `/api/python/calculate` | POST | 推荐计算 |

## 3.10 部署与运行设计

项目提供本地联调脚本：

```text
deploy/scripts/start-local-fullstack.ps1
deploy/scripts/test-local-fullstack.ps1
deploy/scripts/stop-local-fullstack.ps1
```

本地运行端口如下：

| 服务 | 端口 |
|---|---|
| Python 算法服务 | 8000 |
| Spring Boot 后端 | 8080 |
| MySQL | 3306 |

服务器部署脚本包括：

```text
deploy/baota-one-click.sh
deploy/baota.env.example
```

## 3.11 设计特点

本系统设计具有以下特点：

1. 分层清晰：应用层、服务层、算法层、数据层职责明确。
2. 算法独立：推荐算法以 Python 微服务形式独立运行。
3. 语义增强：LLM 只负责解释，不直接参与推荐排序。
4. 数据可追踪：推荐理由保存到数据库，便于展示和复盘。
5. 运行可验证：提供本地启动、停止和测试脚本，支持快速联调。
