# 开发、部署与排错说明

本文档面向开发维护人员，说明项目如何在本地、其他电脑和服务器上复现运行。

## 1. 环境分层

项目分为三类运行环境：

| 环境 | 说明 |
|---|---|
| 本地开发 | VS Code + MySQL + 前端 dev server |
| 本地验收 | 一键启动算法服务和后端，前端单独启动 |
| 服务器部署 | 后端、算法服务、MySQL 和前端静态资源部署到服务器 |

当前仓库不提交：

- `backend/.env`
- `data/movielens/`
- 本地虚拟环境
- 构建产物和日志

## 2. 配置文件

后端读取 `backend/.env` 或系统环境变量。

建议本地 `.env`：

```env
MYSQL_URL=jdbc:mysql://127.0.0.1:3306/movie_recommender?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
MYSQL_USER=root
MYSQL_PASSWORD=你的MySQL密码
ALGORITHM_SERVICE_URL=http://127.0.0.1:8000

LLM_ENABLED=false
LLM_PROVIDER=deepseek
LLM_BASE_URL=https://api.deepseek.com
LLM_MODEL=deepseek-v4-flash
LLM_THINKING_ENABLED=false
LLM_REASONING_EFFORT=high
LLM_EXPLAIN_RECOMMENDATIONS=true
LLM_API_KEY=change_me
```

安全要求：

- 不要把真实 DeepSeek Key 写入 Git。
- 不要把 `backend/.env` 发到公开仓库。
- 前端不保存任何 LLM 密钥。

## 3. 数据库初始化

首次运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\download-movielens-small.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\import-movielens-mysql.ps1
```

导入脚本会把 MovieLens small 写入 MySQL：

- `users`
- `movies`
- `ratings`
- 清理 `recommendations` 缓存

后端使用 Hibernate `ddl-auto:update` 自动补齐新增表，例如 `user_movie_preferences`。

## 4. 数据库迁移建议

课程项目当前使用 `schema.sql` + Hibernate 自动更新，适合快速开发。若要正式化，建议后续引入：

```text
db/migrations/
├── V1__init_schema.sql
├── V2__add_user_movie_preferences.sql
├── V3__add_indexes.sql
└── V4__seed_demo_user.sql
```

建议索引：

```sql
CREATE INDEX idx_ratings_user_id ON ratings(user_id);
CREATE INDEX idx_ratings_movie_id ON ratings(movie_id);
CREATE INDEX idx_recommendations_user_score ON recommendations(user_id, predicted_score);
CREATE INDEX idx_preferences_user_status ON user_movie_preferences(user_id, status);
```

这些索引能提升推荐读取、用户评分查询、电影详情评分统计和显式偏好过滤效率。

## 5. 启动与停止

启动算法服务和后端：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\start-local-fullstack.ps1
```

启动前端：

```powershell
cd frontend
npm install
npm run dev
```

停止服务：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\stop-local-fullstack.ps1
```

## 6. 日志位置

本地脚本会把日志写到：

```text
.local-run/logs/algorithm.log
.local-run/logs/backend.log
```

常见关键字：

- `LLM`：DeepSeek 调用问题。
- `MySQL` 或 `Communications link failure`：数据库连接问题。
- `Address already in use`：端口占用。
- `401`：DeepSeek Key 错误。
- `429`：DeepSeek 限流。

## 7. 常见问题

### 7.1 端口被占用

症状：

```text
Port 8080 already in use
Port 8000 already in use
```

处理：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\stop-local-fullstack.ps1
```

### 7.2 Maven 打包 jar 被占用

症状：

```text
Unable to rename movie-backend-0.0.1-SNAPSHOT.jar
```

原因：后端服务正在运行，占用 jar。

处理：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\stop-local-fullstack.ps1
cd backend
mvn -DskipTests package
```

### 7.3 前端能打开但接口失败

检查：

```powershell
curl.exe "http://127.0.0.1:8080/api/recommend/movie?userId=1&topN=1"
```

如果失败，说明后端未启动或数据库连接失败。

### 7.4 推荐为空

可能原因：

- MovieLens 未导入。
- 当前用户没有足够评分。
- 当前用户标记太多“不感兴趣”。
- 推荐缓存未刷新。

处理：

```powershell
curl.exe -X POST "http://127.0.0.1:8080/api/recommend/refresh?userId=1&topN=5"
```

### 7.5 LLM 回答说没有评分

当前版本后端会把用户评分摘要和显式偏好传给 DeepSeek。如果仍出现该问题，检查：

- 当前是否真的登录了用户。
- 当前用户是否有评分记录。
- 前端是否传了 `topN`。
- 后端是否是最新代码。

## 8. 服务器部署建议

服务器部署时建议：

1. MySQL 独立安装并创建 `movie_recommender` 数据库。
2. 使用环境变量或服务器本地 `.env` 配置数据库和 DeepSeek。
3. 算法服务和后端分别作为长期进程运行。
4. 前端执行 `npm run build` 后部署 `frontend/dist` 静态文件。
5. 使用 Nginx 反向代理：

```text
/api -> 127.0.0.1:8080
前端静态资源 -> frontend/dist
```

生产环境不要直接使用开发模式 `npm run dev`。

## 9. 备份与恢复

建议备份：

- MySQL 数据库。
- `backend/.env`。
- 服务器部署脚本。

MySQL 备份示例：

```bash
mysqldump -u root -p movie_recommender > movie_recommender_backup.sql
```

恢复示例：

```bash
mysql -u root -p movie_recommender < movie_recommender_backup.sql
```
