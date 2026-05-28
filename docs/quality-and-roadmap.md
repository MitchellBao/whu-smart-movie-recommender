# 项目质量现状与后续计划

本文档记录当前项目质量、已知不足和后续工作。写这份文档的目的不是堆功能，而是明确项目现在做到哪里，后面如果继续做应该先改什么。

## 1. 当前状态

当前版本已经跑通以下链路：

```text
MovieLens 数据
  -> MySQL
  -> Python 推荐算法服务
  -> Spring Boot 后端
  -> Vue 前端
  -> DeepSeek 推荐解释
```

主要功能包括：

- 用户注册、登录。
- 电影搜索、分页、筛选。
- 电影详情、评分分布、相似电影。
- 用户评分、修改评分、删除评分。
- 个性化推荐。
- 想看、收藏、不感兴趣。
- 我的评分、我的画像。
- 数据看板。
- DeepSeek 智能问答。

当前测试脚本：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\test-local-fullstack.ps1 -SkipLlmCheck
```

当前期望结果：

```text
Total: 18, Passed: 18, Failed: 0
```

## 2. 已覆盖的测试点

| 类型 | 已覆盖内容 |
|---|---|
| 数据库 | MySQL 连接、数据库存在性检查 |
| 算法服务 | 健康检查、读取 MovieLens 评分 |
| 推荐 | 单用户推荐、多用户推荐、评分后推荐仍可用 |
| 用户 | 注册、登录 |
| 电影 | 搜索、分页、候选、首字母筛选、类型筛选 |
| 详情 | 平均评分、评分人数、评分分布、相似电影 |
| 评分 | 提交评分、非法评分拦截、删除后恢复 |
| 偏好 | 想看、收藏、不感兴趣 |
| 看板 | 系统概览、类型分布、用户画像、推荐画像 |

目前自动测试主要覆盖接口和主链路。前端页面交互仍以手动验收为主。

## 3. 现在还不够完善的地方

### 3.1 用户安全

当前登录系统是课程 MVP 版本。密码字段还没有做正式安全处理。

后续应补：

1. 密码哈希。
2. Token 或 session。
3. 接口权限校验。
4. 登录态过期处理。

这部分如果要继续做，优先级最高的是密码哈希。

### 3.2 数据库管理

现在数据库结构主要依赖 `schema.sql` 和 Hibernate 自动更新。开发阶段方便，但正式部署时不够稳。

后续建议：

```text
db/migrations/
├── V1__init_schema.sql
├── V2__add_user_movie_preferences.sql
├── V3__add_indexes.sql
└── V4__seed_demo_user.sql
```

建议增加的索引：

```sql
CREATE INDEX idx_ratings_user_id ON ratings(user_id);
CREATE INDEX idx_ratings_movie_id ON ratings(movie_id);
CREATE INDEX idx_recommendations_user_score ON recommendations(user_id, predicted_score);
CREATE INDEX idx_preferences_user_status ON user_movie_preferences(user_id, status);
```

### 3.3 推荐算法评估

现在系统能给出推荐，但还没有单独的离线评估报告。

后续可以增加：

| 指标 | 用途 |
|---|---|
| RMSE | 看预测评分误差 |
| MAE | 看平均绝对误差 |
| Precision@K | 看 Top-K 推荐里命中的比例 |
| Recall@K | 看用户喜欢的电影被覆盖多少 |
| Coverage | 看推荐是否覆盖足够多电影 |

建议新增目录：

```text
algorithm-service/evaluation/
├── split_dataset.py
├── evaluate_topk.py
└── metrics_report.md
```

### 3.4 前端结构

现在前端主要写在 `App.vue`。功能已经比较多，继续扩展会不方便维护。

后续可以拆成：

```text
frontend/src/views/
├── RecommendationView.vue
├── MovieLibraryView.vue
├── RatingView.vue
├── ProfileView.vue
├── DashboardView.vue
└── LlmView.vue
```

再抽出通用组件：

```text
MovieDetailPanel.vue
MetricCard.vue
PreferenceButtons.vue
MovieListItem.vue
```

短期内不拆也能跑，但如果后面继续加功能，最好先拆组件。

### 3.5 LLM 调用

当前 DeepSeek 上下文已经包含推荐排名、评分摘要、想看、收藏、不感兴趣。但 LLM 仍有成本和稳定性问题。

后续建议：

- 限制单次提问长度。
- 记录 LLM 调用失败原因。
- 对推荐解释做缓存。
- 控制推荐结果页调用 DeepSeek 的条数。
- 给离线模式提示更明确的前端文案。

## 4. 后续工作优先级

建议按下面顺序做：

| 优先级 | 工作 | 原因 |
|---|---|---|
| P0 | 密码哈希 | 解决最明显的安全问题 |
| P0 | 数据库索引 | 提升评分、推荐、详情查询性能 |
| P1 | 算法评估脚本 | 让推荐效果有指标支撑 |
| P1 | 前端组件拆分 | 降低 `App.vue` 维护压力 |
| P1 | 部署文档和备份流程 | 方便换电脑或服务器运行 |
| P2 | 推荐历史 | 可用于结果复盘，但不是当前主链路必需 |
| P2 | 更复杂的推荐权重 | 可以利用想看、收藏、不感兴趣调整算法 |

## 5. 当前不建议继续加的内容

以下功能短期内不建议做：

- 评论区。
- 社交关注。
- 管理员后台。
- 复杂权限系统。
- 大规模实时训练。

原因是它们会把项目重心从“推荐系统主链路”拉开，答辩收益不高。

## 6. 交付前检查清单

答辩或提交前建议检查：

- `README.md` 能说明怎么安装、启动、测试。
- `docs/architecture.md` 能说明系统设计。
- `docs/api-draft.md` 能说明主要接口。
- `docs/defense-speech.md` 能支撑现场讲解和演示。
- `docs/development-and-ops.md` 能说明常见排错。
- 全栈测试结果为 `18/18 passed`。

只要这些都满足，项目就具备比较完整的课程交付状态。
