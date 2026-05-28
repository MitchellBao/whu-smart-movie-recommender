# 前端说明

本目录是智能电影推荐系统的 Vue 3 / Vite 前端。

## 启动

```powershell
cd frontend
npm install
npm run dev
```

访问：

```text
http://127.0.0.1:5173/
```

## 与后端联调

前端请求统一使用 `/api` 前缀，开发环境由 `vite.config.js` 代理到：

```text
http://127.0.0.1:8080
```

因此启动前端前，应先在项目根目录启动后端和算法服务：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\deploy\scripts\start-local-fullstack.ps1
```

## 当前页面

- 推荐结果：展示推荐列表、推荐分、结构化理由、可信度标签和不感兴趣反馈。
- 电影库与评分：搜索、分页、首字母筛选、类型筛选、电影详情和评分提交。
- 我的评分：统计摘要、排序筛选、快速改分和删除评分。
- 我的画像：评分偏好、显式偏好和最近行为。
- 数据看板：系统规模、类型分布、热门电影、评分画像和推荐画像。
- 智能问答：问题模板、上下文说明、本次问答历史和相关电影入口。

## 注意事项

- DeepSeek API Key 不在前端保存，只能放在后端 `.env`。
- 前端只显示当前登录用户的数据，`userId` 来自登录/注册返回值。
- 电影详情面板只应出现在“推荐结果”和“电影库与评分”页。
