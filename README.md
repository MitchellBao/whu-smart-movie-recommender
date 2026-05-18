# WHU Smart Movie Recommender

## 一、项目简介

本项目是武汉大学《计算机综合项目实践》课程项目，项目名称为：

**面向大规模用户行为数据的智能电影推荐系统设计与实现**

项目目标是设计并实现一个基于用户行为数据的智能电影推荐系统。系统以 MovieLens 等电影评分数据为基础，通过协同过滤、矩阵分解等推荐算法生成个性化电影推荐结果，并结合大语言模型生成自然语言推荐理由，使推荐结果不仅能够展示“推荐什么”，也能够解释“为什么推荐”。

本项目不是单纯的电影信息展示网站，而是一个结合 **传统推荐算法 + 大语言模型语义解释 + Web 系统工程实践** 的综合性软件系统。

---

## 二、项目背景

在传统推荐系统中，系统通常只返回推荐列表或预测评分，用户很难理解推荐结果背后的原因。随着大语言模型的发展，推荐系统可以在原有数值计算结果的基础上进一步生成自然语言解释，从而提升推荐结果的可理解性和交互体验。

本项目围绕电影推荐场景，尝试将传统推荐算法与大语言模型能力结合起来：

- 推荐算法负责计算用户可能感兴趣的电影；
- 后端服务负责业务逻辑、数据访问和服务调度；
- 大语言模型负责根据用户画像、电影信息和算法评分生成推荐理由；
- 前端负责推荐结果、推荐解释和统计信息的展示。

项目在架构上强调“推荐计算”和“语义解释”的职责分离。传统算法负责核心推荐任务，大语言模型作为语义增强层，不直接替代推荐算法。该设计可以避免系统偏离推荐系统主线，同时保留智能化交互特色。

---

## 三、主要功能

### 1. 用户功能

- 用户注册与登录
- 浏览电影信息
- 查看电影类型、标题等基本信息
- 对电影进行评分
- 获取个性化电影推荐结果
- 查看推荐理由
- 通过自然语言方式进行简单查询或交互

### 2. 推荐功能

- 基于用户历史评分生成推荐结果
- 支持基于用户的协同过滤
- 支持基于物品的协同过滤
- 支持 SVD 矩阵分解推荐模型
- 返回 Top-N 推荐电影列表
- 返回预测评分或推荐得分

### 3. 智能解释功能

- 根据用户偏好生成推荐理由
- 根据电影类型、标题、评分等信息生成自然语言解释
- 将推荐理由保存到数据库中，便于前端展示
- 支持后续接入不同大语言模型服务

### 4. 管理与展示功能

- 电影数据管理
- 用户评分数据管理
- 推荐结果缓存
- 推荐结果可视化展示
- 用户行为统计展示

---

## 四、技术栈

| 模块 | 技术 |
|---|---|
| 前端 | Vue.js / Vite |
| 后端 | Spring Boot |
| 推荐算法服务 | Python / Flask |
| 数据库 | MySQL |
| 数据处理 | pandas / numpy / scikit-learn |
| 可视化 | ECharts |
| 大语言模型接口 | 通义千问 API / OpenAI 兼容 API / 其他 LLM API |
| 项目管理 | Git / GitHub |
| 开发环境 | Windows + VS Code |

---

## 五、系统架构

系统采用前后端分离与分层解耦的设计思路，整体划分为四层：

```text
应用层 frontend
    ↓
服务层 backend
    ↓
算法层 algorithm
    ↓
数据层 database
```

### 1. 应用层

应用层主要由 Vue.js 实现，负责用户交互与页面展示，包括电影列表、推荐结果、推荐理由、用户评分界面和可视化统计页面。

### 2. 服务层

服务层由 Spring Boot 实现，负责处理前端请求、用户管理、评分管理、数据库访问、推荐服务调用和大语言模型 API 调用。

### 3. 算法层

算法层由 Python 实现，主要负责推荐算法计算，包括数据预处理、评分矩阵构建、协同过滤和矩阵分解等任务。

### 4. 数据层

数据层由 MySQL 实现，用于存储用户信息、电影信息、评分信息、标签信息、推荐结果和推荐理由。

---

## 六、核心流程

### 推荐生成与解释流程

```text
1. 用户在前端发起推荐请求
2. Spring Boot 后端接收请求
3. 后端读取用户历史评分和电影信息
4. 后端调用 Python 推荐算法服务
5. Python 服务返回 Top-N 推荐电影及预测评分
6. 后端组装 Prompt
7. 后端调用大语言模型 API
8. 大语言模型生成推荐理由
9. 后端将推荐结果与推荐理由写入数据库
10. 前端展示电影推荐列表和自然语言解释
```

该流程对应项目中“计算结果 + 语义表达”的核心设计。Python 算法服务负责数值计算，Spring Boot 负责服务编排与 Prompt 组装，LLM 负责推荐理由生成，前端负责最终展示。已有阶段总结中也明确将 LLM 定位为语义增强层，而不是核心推荐算法的替代者。:contentReference[oaicite:1]{index=1}

---

## 七、项目目录结构

```text
whu-smart-movie-recommender/
├── README.md
├── frontend/
│   ├── package.json
│   ├── index.html
│   └── src/
│       ├── App.vue
│       └── main.js
├── backend/
│   ├── pom.xml
│   └── src/
│       └── main/
│           ├── java/
│           └── resources/
├── algorithm/
│   ├── app.py
│   ├── requirements.txt
│   └── recommender/
├── database/
│   └── schema.sql
├── docs/
│   ├── 01-topic-and-team.md
│   ├── 02-requirements-draft.md
│   ├── architecture.md
│   └── api-draft.md
└── deploy/
    └── docker-compose.yml
```

---

## 八、快速启动

### 1. 克隆项目

```powershell
git clone https://github.com/MitchellBao/whu-smart-movie-recommender.git
cd whu-smart-movie-recommender
```

---

### 2. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

默认访问地址：

```text
http://localhost:5173/
```

---

### 3. 启动 Python 推荐算法服务

```powershell
cd algorithm
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

测试地址：

```text
http://127.0.0.1:5000/
http://127.0.0.1:5000/recommend/1
```

示例返回：

```json
{
  "user_id": 1,
  "recommendations": [
    {
      "movie_id": 1,
      "title": "Toy Story",
      "score": 4.8
    },
    {
      "movie_id": 2,
      "title": "Jumanji",
      "score": 4.6
    }
  ]
}
```

---

### 4. 启动后端

进入后端目录：

```powershell
cd backend
```

使用 Maven 启动：

```powershell
.\mvnw spring-boot:run
```

健康检查接口：

```text
http://localhost:8080/api/health
```

预期返回：

```text
backend is running
```

---

### 5. 初始化数据库

进入 MySQL 后执行：

```sql
source database/schema.sql;
```

或者复制 `database/schema.sql` 中的 SQL 语句到 MySQL Workbench / DataGrip / VS Code 数据库插件中执行。

---

## 九、数据库设计概要

系统初步包含以下数据表：

| 表名 | 说明 |
|---|---|
| users | 用户信息表 |
| movies | 电影信息表 |
| ratings | 用户评分表 |
| tags | 电影标签表 |
| recommendations | 推荐结果表 |

其中，`recommendations` 表中设置了 `reason` 字段，用于保存大语言模型生成的推荐理由。该字段是本项目区别于普通推荐系统的重要设计之一，用于打通“算法结果 → LLM 解释 → 前端展示”的数据链路。

---

## 十、API 草案

### 1. 健康检查

```http
GET /api/health
```

返回：

```text
backend is running
```

---

### 2. 获取电影列表

```http
GET /api/movies
```

---

### 3. 提交用户评分

```http
POST /api/ratings
```

请求体示例：

```json
{
  "userId": 1,
  "movieId": 10,
  "rating": 4.5
}
```

---

### 4. 获取推荐结果

```http
GET /api/recommendations/{userId}
```

返回示例：

```json
{
  "userId": 1,
  "recommendations": [
    {
      "movieId": 1,
      "title": "Toy Story",
      "predictedScore": 4.8,
      "reason": "该电影与用户过往偏好的动画和冒险类型较为接近。"
    }
  ]
}
```

---

### 5. 调用 Python 推荐服务

```http
GET http://127.0.0.1:5000/recommend/{userId}
```

---

### 6. 生成推荐理由

```http
POST /api/llm/explain
```

请求体示例：

```json
{
  "userId": 1,
  "movieId": 1,
  "title": "Toy Story",
  "genres": "Animation|Children|Comedy",
  "predictedScore": 4.8
}
```

---

## 十一、当前开发进度

### 第一阶段：项目初始化与工程骨架

- [x] 创建 GitHub 仓库
- [x] 初始化前端 Vue 项目
- [ ] 初始化 Spring Boot 后端项目
- [ ] 初始化 Python 推荐算法服务
- [ ] 创建数据库初版表结构
- [ ] 编写项目 README
- [ ] 编写 docs 文档草稿

### 第二阶段：需求分析与系统设计

- [ ] 完成需求规格说明书
- [ ] 完成系统设计说明书
- [ ] 完成数据库详细设计
- [ ] 完成 API 接口设计
- [ ] 完成推荐流程设计

### 第三阶段：核心功能开发

- [ ] 完成 MovieLens 数据导入
- [ ] 完成基础电影列表接口
- [ ] 完成评分接口
- [ ] 完成协同过滤推荐算法
- [ ] 完成后端调用 Python 算法服务
- [ ] 完成推荐结果展示页面

### 第四阶段：智能解释与系统集成

- [ ] 接入大语言模型 API
- [ ] 完成推荐理由生成
- [ ] 完成推荐理由入库
- [ ] 完成前后端联调
- [ ] 完成基础测试

### 第五阶段：答辩与报告整理

- [ ] 完成最终项目实践报告
- [ ] 完成项目演示
- [ ] 完成答辩 PPT
- [ ] 整理 GitHub 提交记录

---

## 十二、团队分工

| 成员 | 角色 | 主要职责 |
|---|---|---|
| 鲍明颉 | 组长 / Scrum Master / 系统架构负责人 | 项目统筹、系统架构设计、后端与接口设计、文档整理 |
| 成员 A | 算法与数据处理 | MovieLens 数据处理、推荐算法实现、算法评估 |
| 成员 B | 前端与测试 | Vue 前端页面、推荐结果展示、系统测试 |

课程要求团队完成架构设计、软件分析、开发、测试、部署和运维等角色工作，并通过 Git 查看代码与文档贡献，因此项目将持续通过 GitHub 管理开发记录。:contentReference[oaicite:2]{index=2}

---

## 十三、开发规范

### 1. Git 提交规范

建议提交信息采用英文动词开头：

```text
init vue frontend
init spring boot backend
init python recommendation service
add database schema
add api draft
update project readme
```

### 2. 分支规范

第一阶段可以先使用 `main` 分支。后续功能增多后，可拆分为：

```text
main
dev
feature/frontend
feature/backend
feature/algorithm
```

### 3. 代码规范

- 前端组件命名清晰，页面与组件分离；
- 后端接口路径统一以 `/api` 开头；
- Python 算法服务返回统一 JSON 格式；
- 数据库字段命名使用小写字母和下划线；
- 不将 `.env`、虚拟环境、`node_modules`、编译产物提交到 GitHub。

---

## 十四、后续计划

后续开发将优先完成以下内容：

1. 完成 Spring Boot 后端健康检查接口；
2. 完成 Python 推荐算法服务假数据接口；
3. 完成 MySQL 数据库初版建表；
4. 导入 MovieLens 数据集；
5. 实现基础电影查询与评分接口；
6. 实现基础协同过滤推荐算法；
7. 完成后端与算法服务联调；
8. 接入大语言模型生成推荐理由；
9. 完成前端推荐结果展示页面；
10. 整理项目报告与答辩材料。

---

## 十五、项目说明

本项目当前处于课程实践早期阶段，主要目标是完成系统工程骨架搭建、技术路线验证和核心模块接口设计。后续将逐步补充完整推荐算法、数据库导入、前后端联调和大语言模型推荐解释功能。