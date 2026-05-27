# API 接口设计草案

## 1. 接口设计原则

系统采用前后端分离架构。前端统一请求 Spring Boot 后端，后端根据业务需要访问 MySQL、调用 Python 算法服务或调用 LLM 服务。

接口设计原则：

- 前端统一使用 `/api` 前缀。
- 前端不直接调用 Python 算法服务。
- Java 后端与 Python 算法服务通过 HTTP JSON 通信。
- 返回结构保持稳定，便于前端联调和自动化测试。
- 本地前端开发通过 Vite proxy 将 `/api` 转发到 `http://127.0.0.1:8080`。

## 2. 前端调用后端接口

### 2.1 用户注册

```http
POST /api/user/register
Content-Type: application/json
```

请求体：

```json
{
  "username": "demo",
  "password": "test123456",
  "age": 20,
  "gender": "male"
}
```

### 2.2 用户登录

```http
POST /api/user/login
Content-Type: application/json
```

请求体：

```json
{
  "username": "demo",
  "password": "test123456"
}
```

返回结构与注册接口一致。前端保存返回的 `userId`，后续推荐和评分都使用该用户编号。

### 2.3 分页浏览电影

```http
GET /api/movie/page?keyword=Matrix&page=1&pageSize=12
```

支持按首字母和类型筛选：

```http
GET /api/movie/page?keyword=&initial=A&genre=Comedy&page=1&pageSize=12
```

返回示例：

```json
{
  "code": 0,
  "data": {
    "items": [
      {
        "movieId": 2571,
        "title": "Matrix, The (1999)",
        "releaseYear": 1999,
        "genres": "Action|Sci-Fi|Thriller"
      }
    ],
    "page": 1,
    "pageSize": 12,
    "totalPages": 3,
    "totalItems": 27
  }
}
```

### 2.4 获取推荐电影

```http
GET /api/recommend/movie?userId=1&topN=5
```

返回示例：

```json
{
  "code": 0,
  "data": [
    {
      "movieId": 589,
      "title": "Terminator 2: Judgment Day (1991)",
      "genres": "Action|Sci-Fi",
      "score": 7.67408,
      "reason": "当前为离线模式：未启用 LLM 密钥，返回默认解释。"
    }
  ]
}
```

说明：

- `score` 是算法排序分，用于比较推荐优先级。
- `score` 不等同于用户评分满分 5 分。
- 当前推荐是确定性排序，同一用户、同一批评分数据和同一 `topN` 下结果会保持稳定。
- 用户提交新评分后，系统会重新计算该用户推荐。

### 2.5 提交用户评分

```http
POST /api/rating/submit
Content-Type: application/json
```

请求体：

```json
{
  "userId": 1,
  "movieId": 1,
  "score": 4.5
}
```

评分规则：

- 最低分：`0.5`
- 最高分：`5.0`
- 步长：`0.5`
- 非法示例：`0.25`、`0`、`3.3`、`5.5`

职责：

- 保存用户评分到 `ratings` 表。
- 如果同一用户已经评价过同一部电影，则更新旧评分，不新增重复当前评分。
- 刷新该用户的推荐缓存。
- 支持前端提交评分后重新获取推荐结果。

### 2.6 查询用户评分列表

```http
GET /api/rating/user?userId=1
```

返回示例：

```json
{
  "code": 0,
  "data": [
    {
      "ratingId": 123,
      "movieId": 2571,
      "title": "Matrix, The (1999)",
      "genres": "Action|Sci-Fi|Thriller",
      "score": 4.5,
      "timestamp": 1779417600
    }
  ]
}
```

职责：

- 查询当前用户已经评分的电影。
- 返回电影标题、类型、评分和时间戳。
- 支持前端“我的评分”模块展示和修改回填。

### 2.7 LLM 状态

```http
GET /api/llm/status
```

返回示例：

```json
{
  "code": 0,
  "enabled": true,
  "configured": true,
  "provider": "deepseek",
  "model": "deepseek-v4-flash",
  "baseUrl": "https://api.deepseek.com"
}
```

说明：

- `enabled` 表示是否开启 LLM 调用。
- `configured` 表示是否已配置 API Key。
- 前端根据该接口显示“DeepSeek 已启用”或“DeepSeek 离线模式”。
- 默认模型为 `deepseek-v4-flash`；如需思考模式，可配置 `deepseek-v4-pro` 与 `LLM_THINKING_ENABLED=true`。

### 2.8 LLM 推荐问答

```http
POST /api/llm/query
Content-Type: application/json
```

请求体：

```json
{
  "userId": 1,
  "queryText": "推荐一部烧脑科幻片"
}
```

职责：

- 接收用户自然语言查询。
- 获取当前用户相关推荐结果作为上下文。
- 调用 DeepSeek OpenAI-compatible API 生成回答。
- 未启用 LLM 密钥时返回离线降级文案。

## 2.9 后台推荐刷新与电影筛选辅助接口

评分提交接口只负责快速保存评分。前端随后调用刷新接口，由后端异步重新计算推荐并生成 DeepSeek 推荐理由。刷新期间前端保留旧推荐结果，等待状态结束后再重新读取推荐列表。

```http
POST /api/recommend/refresh?userId=1&topN=5
GET /api/recommend/refresh/status?userId=1
```

电影库支持搜索候选、A-Z 首字母筛选和类型筛选：

```http
GET /api/movie/suggest?keyword=Matrix&limit=8
GET /api/movie/genres
GET /api/movie/page?keyword=&initial=A&genre=Comedy&page=1&pageSize=12
```

## 3. 后端调用算法服务接口

### 3.1 算法服务健康检查

```http
GET /api/python/health
```

返回示例：

```json
{
  "status": "ok",
  "ratingCount": 100836
}
```

### 3.2 推荐计算接口

```http
POST /api/python/calculate
Content-Type: application/json
```

请求体：

```json
{
  "userId": 1,
  "topN": 5
}
```

职责：

- 从 MySQL `ratings` 表读取 MovieLens 评分数据。
- 根据用户 ID 和推荐数量计算推荐结果。
- 返回推荐电影编号、算法排序分和算法侧推荐说明。

## 4. 当前前端联调状态

前端已经完成以下联调：

- 通过 `/api/user/register` 和 `/api/user/login` 获取当前用户 ID。
- 通过浏览器 `localStorage` 记住当前用户。
- 通过 `/api/movie/page` 分页浏览、搜索电影、跳转指定页码，并支持首字母和类型筛选。
- 通过 `/api/movie/suggest` 和 `/api/movie/genres` 支持电影候选和类型筛选。
- 通过 `/api/recommend/movie` 获取推荐列表。
- 区分“用户评分”和“推荐排序分”。
- 通过 `/api/rating/submit` 快速保存评分，再通过 `/api/recommend/refresh` 后台异步更新推荐。
- 通过 `/api/rating/user` 查看已评分电影，并支持回填修改评分。
- 通过 `/api/llm/status` 显示 DeepSeek 启用状态。
- 通过 `/api/llm/query` 进行推荐问答，未配置 LLM 密钥时使用离线降级回答。
## 5. 数据可视化接口

### 5.1 系统总览

```http
GET /api/stats/overview
```

返回电影总数、用户总数、评分总数和平均评分，用于数据看板顶部指标卡。

### 5.2 电影类型分布

```http
GET /api/stats/genres
```

根据 `movies.genres` 拆分并统计各类型电影数量，用于展示电影库覆盖情况。

### 5.3 热门电影

```http
GET /api/stats/top-rated?limit=10
```

按评分数量返回热门电影 TopN，同时返回平均评分，用于展示 MovieLens 数据集中最常被评价的电影。

### 5.4 当前用户评分画像

```http
GET /api/stats/user?userId=1
```

返回当前用户评分数量、平均评分、评分分布和类型偏好，用于说明用户画像如何支撑推荐。

### 5.5 当前推荐画像

```http
GET /api/stats/recommendation?userId=1
```

返回当前用户推荐缓存中的类型分布和推荐分排行，用于解释本次推荐列表的整体倾向。

前端“数据看板”菜单会统一调用这些接口并使用 ECharts 渲染图表。
