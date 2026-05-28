# 手动测试与答辩演示流程

本文档用于本地验收、答辩前彩排和组员交接。目标是用最短路径证明系统主链路可运行。

## 1. 启动前检查

确认已安装：

- JDK 17 或 21
- Maven
- Python 3.10+
- Node.js 20+
- MySQL 8

确认 `backend/.env` 已配置 MySQL：

```env
MYSQL_URL=jdbc:mysql://127.0.0.1:3306/movie_recommender?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
MYSQL_USER=root
MYSQL_PASSWORD=你的MySQL密码
ALGORITHM_SERVICE_URL=http://127.0.0.1:8000
```

## 2. 准备 MovieLens 数据

首次运行或换电脑运行时执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\download-movielens-small.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\import-movielens-mysql.ps1
```

预期数据量：

```text
users:   610
movies:  9742
ratings: 100836
```

## 3. 启动服务

项目根目录执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\start-local-fullstack.ps1
```

另开终端启动前端：

```powershell
cd frontend
npm install
npm run dev
```

访问：

```text
http://127.0.0.1:5173/
```

## 4. 答辩演示推荐顺序

### 4.1 登录或注册

1. 打开前端页面。
2. 注册一个新用户，或登录已有用户。
3. 说明：系统会保存当前用户，不需要手动输入 `userId`。

### 4.2 浏览电影库并评分

1. 进入“电影库与评分”。
2. 搜索 `Matrix` 或选择类型筛选。
3. 点击一部电影查看详情。
4. 展示详情中的年份、类型、平均评分、评分人数、评分分布和相似电影。
5. 输入评分，例如 `4.5`，点击保存。

说明要点：

- 用户评分范围为 `0.5` 到 `5.0`。
- 评分会进入 MySQL 的 `ratings` 表。
- 推荐系统会异步刷新当前用户推荐。

### 4.3 查看推荐结果

1. 进入“推荐结果”。
2. 查看推荐列表、推荐分、结构化推荐理由和可信度标签。
3. 修改推荐数量，例如从 `5` 改为 `10`。
4. 点击刷新推荐。

说明要点：

- 推荐分是算法排序分，不等同于用户评分。
- 同一批数据下推荐结果稳定。
- 用户新增或修改评分后推荐会重新计算。

### 4.4 显式偏好反馈

1. 在推荐结果中点击“不感兴趣”。
2. 该电影应立即从当前列表移除。
3. 进入“我的画像”，查看不感兴趣数量变化。
4. 在电影详情中标记“想看”或“收藏”。

说明要点：

- 这些数据保存到 `user_movie_preferences`。
- “不感兴趣”会作为后续推荐过滤条件。

### 4.5 我的评分

1. 进入“我的评分”。
2. 展示已评分数量、平均评分、最高频类型、最近评分时间。
3. 使用排序和筛选。
4. 直接修改某条评分并保存。
5. 删除某条评分，再观察推荐刷新。

说明要点：

- 这里是用户画像的重要数据来源。
- 修改或删除评分都会影响后续推荐。

### 4.6 我的画像

1. 进入“我的画像”。
2. 展示评分偏好、最近评分、想看、收藏、不感兴趣。
3. 说明：画像页把隐式反馈和显式反馈集中展示。

### 4.7 数据看板

1. 进入“数据看板”。
2. 展示电影总数、用户数、评分数、平均评分。
3. 展示类型分布、热门电影、用户评分分布、推荐分分布。

说明要点：

- 数据看板用于解释数据规模和推荐结果特征。
- 图表不参与推荐计算，只用于可视化说明。

### 4.8 智能问答

1. 进入“智能问答”。
2. 查看“当前会参考”的上下文摘要。
3. 点击模板：“解释当前推荐第一名为什么适合我”。
4. 再问：“根据我的评分推荐一部电影”。

说明要点：

- 前端会传 `userId`、`queryText`、`topN`。
- 后端会读取推荐列表、用户评分、想看、收藏、不感兴趣。
- LLM 只做语义解释，不参与推荐排序。

## 5. 命令行快速验收

```powershell
curl.exe http://127.0.0.1:8000/api/python/health
curl.exe "http://127.0.0.1:8080/api/recommend/movie?userId=1&topN=5"
curl.exe "http://127.0.0.1:8080/api/movie/detail?movieId=2571&userId=1"
curl.exe "http://127.0.0.1:8080/api/rating/user?userId=1"
curl.exe "http://127.0.0.1:8080/api/movie/preference?userId=1"
curl.exe "http://127.0.0.1:8080/api/llm/status"
```

完整自动测试：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\test-local-fullstack.ps1 -SkipLlmCheck
```

预期：

```text
Total: 18, Passed: 18, Failed: 0
```

## 6. 停止服务

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\stop-local-fullstack.ps1
```
