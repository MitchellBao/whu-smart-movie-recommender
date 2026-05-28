# 质量改进与后续路线

本文档用于说明项目当前质量状态、已覆盖测试和后续可改进方向，便于答辩时说明系统边界。

## 1. 当前质量状态

已完成：

- MovieLens 数据导入 MySQL。
- Python 算法服务读取 MySQL 评分数据。
- Spring Boot 后端统一编排推荐、评分、偏好、LLM 和看板接口。
- Vue 前端完成注册登录、推荐、电影库、评分、画像、看板和问答。
- DeepSeek 未配置时支持离线降级。
- 全栈测试脚本覆盖核心链路。

当前全栈测试命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\test-local-fullstack.ps1 -SkipLlmCheck
```

当前预期：

```text
Total: 18, Passed: 18, Failed: 0
```

## 2. 已覆盖测试点

- MySQL 连接。
- 算法服务健康检查。
- 推荐接口返回完整电影信息。
- 多用户推荐稳定性。
- 用户注册和登录。
- 电影搜索、分页、候选和筛选。
- 电影详情、评分分布和相似电影。
- 想看、收藏、不感兴趣显式反馈。
- 评分提交。
- 后台异步推荐刷新。
- 用户评分列表。
- 数据看板统计接口。
- 评分边界校验。
- 评分删除和恢复。

## 3. 当前限制

### 3.1 用户安全

当前用户系统是课程 MVP 版本，重点是跑通推荐链路。正式环境应补充：

- 密码哈希。
- Token/session。
- 登录态过期。
- 接口鉴权。

### 3.2 推荐算法

当前算法适合 MovieLens small 课程演示。后续可改进：

- 使用更多离线评估指标。
- 对想看、收藏、不感兴趣加入算法权重。
- 记录推荐历史用于 A/B 比较。
- 对冷启动用户设计更明确的引导评分流程。

### 3.3 数据库治理

当前采用 `schema.sql` + Hibernate 自动更新，开发效率高，但正式环境建议：

- 引入迁移脚本。
- 加索引。
- 做备份恢复。
- 区分开发、测试、生产数据库。

### 3.4 前端结构

当前前端集中在 `App.vue`，功能完整但文件较大。后续可拆分：

```text
frontend/src/views/
├── RecommendationView.vue
├── MovieLibraryView.vue
├── RatingView.vue
├── ProfileView.vue
├── DashboardView.vue
└── LlmView.vue
```

同时可拆出通用组件：

```text
MovieDetailPanel.vue
MetricCard.vue
MovieListItem.vue
PreferenceButtons.vue
```

## 4. 推荐系统评估路线

推荐系统答辩中可以说明后续评估指标：

| 指标 | 说明 |
|---|---|
| RMSE | 衡量预测评分和真实评分的误差 |
| MAE | 衡量平均绝对误差 |
| Precision@K | Top-K 推荐中用户真正喜欢的比例 |
| Recall@K | 用户喜欢的电影中被推荐覆盖的比例 |
| Coverage | 推荐结果覆盖电影库的范围 |
| Novelty | 推荐是否避免只推热门电影 |

建议后续新增：

```text
algorithm-service/evaluation/
├── split_dataset.py
├── evaluate_topk.py
└── metrics_report.md
```

## 5. 安全改进路线

优先级：

1. 密码哈希：避免明文密码。
2. Token/session：避免前端只靠 localStorage 用户对象。
3. 后端鉴权：接口只允许访问当前用户数据。
4. LLM Key 管理：只允许环境变量或服务器私有配置。
5. 输入校验：限制用户名、评分、分页参数和 LLM 提问长度。

## 6. 性能改进路线

已做：

- 推荐结果按用户缓存到 `recommendations`。
- 评分提交后异步刷新推荐。
- 前端分页浏览电影。
- 不感兴趣过滤在后端完成。

建议后续：

- 给 `ratings.user_id`、`ratings.movie_id`、`recommendations.user_id` 加索引。
- 电影详情评分分布可缓存。
- LLM 推荐理由可批量生成或限流生成。
- 算法服务可保存模型中间结果，避免每次重建矩阵。

## 7. 文档与交付路线

答辩前建议保持以下文档可用：

- `README.md`：项目入口。
- `docs/manual-test-guide.md`：演示流程。
- `docs/architecture.md`：系统设计。
- `docs/api-draft.md`：接口说明。
- `docs/development-and-ops.md`：部署排错。
- `docs/defense-speech.md`：答辩讲稿。

这几份文档已经覆盖“怎么跑、怎么测、怎么讲、出了问题怎么查”。
