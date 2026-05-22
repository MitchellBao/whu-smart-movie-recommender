# API 接口设计草案

## 1. 接口设计原则

第一阶段确定系统采用前后端分离架构。前端统一调用 Spring Boot 后端接口，后端根据业务需要访问数据库、调用 Python 算法服务或调用 LLM 服务。

接口设计遵循以下原则：

- 对前端暴露统一 `/api` 前缀。
- 前端不直接调用 Python 算法服务。
- Java 后端与 Python 算法服务通过 HTTP JSON 通信。
- 返回数据尽量保持结构稳定，便于前端联调。

## 2. 前端调用后端接口

### 2.1 获取推荐电影

```http
GET /api/recommend/movie?userId=1&topN=3
```

请求参数：

| 参数 | 类型 | 是否必填 | 说明 |
|---|---|---|---|
| userId | int | 是 | 用户编号 |
| topN | int | 否 | 推荐数量，默认 10 |

返回示例：

```json
{
  "code": 0,
  "data": [
    {
      "movieId": 6,
      "title": "Heat",
      "genres": "Action|Crime|Thriller",
      "score": 5.66919,
      "reason": "当前为离线模式：未启用LLM密钥，返回默认解释。"
    }
  ]
}
```

接口职责：

- 接收前端推荐请求。
- 调用推荐业务服务。
- 返回推荐电影列表、预测分数和推荐理由。

### 2.2 提交用户评分

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

- 保存用户评分记录。
- 评分变化后触发推荐结果刷新。

### 2.3 LLM 推荐问答

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

返回示例：

```json
{
  "code": 0,
  "responseText": "当前为离线模式：未启用LLM密钥，返回默认解释。",
  "relatedMovies": [
    {
      "movieId": 6,
      "title": "Heat",
      "genres": "Action|Crime|Thriller",
      "score": 5.66919,
      "reason": "当前为离线模式：未启用LLM密钥，返回默认解释。"
    }
  ]
}
```

接口职责：

- 接收用户自然语言查询。
- 获取当前用户相关推荐结果。
- 调用 LLM 客户端生成回答；未启用 LLM 时返回离线降级文案。

## 3. 后端调用算法服务接口

### 3.1 算法服务健康检查

```http
GET /api/python/health
```

返回示例：

```json
{
  "status": "ok"
}
```

接口职责：

- 检查 Python 推荐算法服务是否正常运行。

### 3.2 推荐计算接口

```http
POST /api/python/calculate
Content-Type: application/json
```

请求体：

```json
{
  "userId": 1,
  "topN": 10
}
```

返回示例：

```json
{
  "userId": 1,
  "recommendations": [
    {
      "movieId": 6,
      "score": 5.66919,
      "reason": "基于相似用户偏好与矩阵分解隐语义特征生成"
    }
  ]
}
```

接口职责：

- 根据用户编号和推荐数量计算推荐结果。
- 返回推荐电影编号、预测得分和算法侧推荐说明。

## 4. 第一阶段接口范围

第一阶段接口草案覆盖推荐系统主链路所需的最小接口集合：

- 推荐结果获取。
- 用户评分提交。
- LLM 推荐问答入口。
- Java 后端与 Python 算法服务内部调用。

这些接口用于支撑工程骨架联调和系统职责边界验证。
