# API 接口设计草案

## 1. 接口设计原则

系统采用前后端分离架构。前端统一调用 Spring Boot 后端接口，后端根据业务需要访问 MySQL、调用 Python 算法服务或调用 LLM 服务。

接口设计原则：

- 对前端暴露统一 `/api` 前缀。
- 前端不直接调用 Python 算法服务。
- Java 后端与 Python 算法服务通过 HTTP JSON 通信。
- 返回结构保持稳定，便于前端联调。
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

返回示例：

```json
{
  "code": 0,
  "data": {
    "userId": 611,
    "username": "demo",
    "age": 20,
    "gender": "male"
  }
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

### 2.3 搜索电影

```http
GET /api/movie/search?keyword=Matrix&limit=12
```

返回示例：

```json
{
  "code": 0,
  "data": [
    {
      "movieId": 2571,
      "title": "Matrix, The (1999)",
      "releaseYear": 1999,
      "genres": "Action|Sci-Fi|Thriller"
    }
  ]
}
```

### 2.4 获取推荐电影

```http
GET /api/recommend/movie?userId=1&topN=5
```

参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| userId | int | 是 | 用户编号 |
| topN | int | 否 | 推荐数量，默认 10 |

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
      "reason": "当前为离线模式：未启用LLM密钥，返回默认解释。"
    }
  ]
}
```

接口职责：

- 接收前端推荐请求。
- 调用推荐业务服务。
- 当推荐缓存不足时刷新推荐结果。
- 返回电影 ID、标题、类型、得分和推荐理由。

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

返回示例：

```json
{
  "code": 0,
  "message": "ok"
}
```

接口职责：

- 保存用户评分到 `ratings` 表。
- 清理或刷新该用户的推荐缓存。
- 支持前端提交评分后重新获取推荐结果。

### 2.6 LLM 推荐问答

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

接口职责：

- 接收用户自然语言查询。
- 获取当前用户相关推荐结果。
- 调用 LLM 客户端生成回答。
- 未启用 LLM 时返回离线降级文案。

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

接口职责：

- 检查 Python 算法服务是否运行。
- 检查算法服务是否能读取 MySQL `ratings` 表。

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

返回示例：

```json
{
  "userId": 1,
  "recommendations": [
    {
      "movieId": 589,
      "score": 7.67408,
      "reason": "基于 MovieLens 评分、相似用户偏好与矩阵分解隐语义特征生成"
    }
  ]
}
```

接口职责：

- 从 MySQL `ratings` 表读取 MovieLens 评分数据。
- 根据用户 ID 和推荐数量计算推荐结果。
- 返回推荐电影编号、预测得分和算法侧推荐说明。

## 4. 当前前端联调状态

前端已完成初步联调：

- 通过 `/api/user/register` 和 `/api/user/login` 获取当前用户 ID。
- 通过浏览器 `localStorage` 记住当前用户。
- 通过 `/api/movie/search` 浏览和搜索电影，避免用户手动记忆 `movieId`。
- 通过 `/api/recommend/movie` 获取推荐列表。
- 展示 `title`、`genres`、`score`、`reason`。
- 通过 `/api/rating/submit` 提交评分。
- 提交评分后自动刷新推荐列表。

当前暂不直接在前端调用 Python 算法服务，也暂未完成 LLM 问答界面。
