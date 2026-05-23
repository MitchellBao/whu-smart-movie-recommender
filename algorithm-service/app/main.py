import os
from pathlib import Path
from typing import List
from urllib.parse import urlparse

import numpy as np
import pandas as pd
import pymysql
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from sklearn.decomposition import TruncatedSVD


class RecommendRequest(BaseModel):
    userId: int
    topN: int = 10


class RecommendationItem(BaseModel):
    movieId: int
    score: float
    reason: str


class RecommendResponse(BaseModel):
    userId: int
    recommendations: List[RecommendationItem]


app = FastAPI(title="WHU Recommendation Algorithm Service")


def _load_env_file() -> None:
    repo_root = Path(__file__).resolve().parents[2]
    env_file = repo_root / "backend" / ".env"
    if not env_file.exists():
        return
    for raw_line in env_file.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip())


def _mysql_config() -> dict[str, object]:
    _load_env_file()
    jdbc_url = os.getenv(
        "MYSQL_URL",
        "jdbc:mysql://127.0.0.1:3306/movie_recommender?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai",
    )
    parsed = urlparse(jdbc_url.removeprefix("jdbc:"))
    return {
        "host": parsed.hostname or "127.0.0.1",
        "port": parsed.port or 3306,
        "user": os.getenv("MYSQL_USER", "root"),
        "password": os.getenv("MYSQL_PASSWORD", "root"),
        "database": parsed.path.lstrip("/") or "movie_recommender",
        "charset": "utf8mb4",
    }


def _load_ratings_from_mysql() -> pd.DataFrame:
    config = _mysql_config()
    query = """
        SELECT user_id, movie_id, score AS rating
        FROM ratings
        WHERE score IS NOT NULL
    """
    connection = pymysql.connect(**config)
    try:
        ratings = pd.read_sql_query(query, connection)
    finally:
        connection.close()

    if ratings.empty:
        raise ValueError("ratings table is empty; import MovieLens data first")

    ratings["user_id"] = ratings["user_id"].astype(int)
    ratings["movie_id"] = ratings["movie_id"].astype(int)
    ratings["rating"] = ratings["rating"].astype(float)
    return ratings


def _popular_recommend(ratings: pd.DataFrame, top_n: int) -> List[RecommendationItem]:
    popular = (
        ratings.groupby("movie_id")["rating"]
        .agg(["mean", "count"])
        .query("count >= 2")
        .sort_values(["mean", "count"], ascending=False)
        .head(top_n)
    )
    if popular.empty:
        popular = ratings.groupby("movie_id")["rating"].mean().sort_values(ascending=False).head(top_n).to_frame("mean")

    return [
        RecommendationItem(
            movieId=int(movie_id),
            score=float(row["mean"]),
            reason="新用户默认按 MovieLens 全站高评分电影推荐",
        )
        for movie_id, row in popular.iterrows()
    ]


def _hybrid_recommend(user_id: int, top_n: int) -> List[RecommendationItem]:
    ratings = _load_ratings_from_mysql()
    matrix = ratings.pivot_table(index="user_id", columns="movie_id", values="rating").fillna(0.0)

    if matrix.empty:
        raise ValueError("rating matrix is empty")

    if user_id not in matrix.index:
        return _popular_recommend(ratings, top_n)

    user_vector = matrix.loc[user_id]
    similarities = matrix.corrwith(user_vector, axis=1).fillna(0.0).drop(labels=[user_id], errors="ignore")
    top_neighbors = similarities.sort_values(ascending=False).head(20).index.tolist()

    seen_movies = set(ratings[ratings["user_id"] == user_id]["movie_id"].tolist())
    neighbor_movies = ratings[ratings["user_id"].isin(top_neighbors)]["movie_id"].unique().tolist()
    candidates = [movie_id for movie_id in neighbor_movies if movie_id not in seen_movies]
    if not candidates:
        candidates = [movie_id for movie_id in matrix.columns.tolist() if movie_id not in seen_movies]
    if not candidates:
        return _popular_recommend(ratings, top_n)

    column_count = len(matrix.columns)
    n_components = min(20, column_count - 1) if column_count > 1 else 1
    svd = TruncatedSVD(n_components=max(1, n_components), random_state=42)
    user_latent = svd.fit_transform(matrix.values)
    item_latent = svd.components_.T

    user_index = list(matrix.index).index(user_id)
    movie_columns = list(matrix.columns)
    base_bias = ratings.groupby("movie_id")["rating"].mean().to_dict()
    results: list[RecommendationItem] = []

    for movie_id in candidates:
        movie_index = movie_columns.index(movie_id)
        score = float(np.dot(user_latent[user_index], item_latent[movie_index]))
        score += float(base_bias.get(movie_id, 0.0))
        results.append(
            RecommendationItem(
                movieId=int(movie_id),
                score=round(score, 5),
                reason="基于 MovieLens 评分、相似用户偏好与矩阵分解隐语义特征生成",
            )
        )

    results.sort(key=lambda item: item.score, reverse=True)
    return results[:top_n]


@app.get("/api/python/health")
def health() -> dict:
    try:
        config = _mysql_config()
        connection = pymysql.connect(**config)
        try:
            with connection.cursor() as cursor:
                cursor.execute("SELECT COUNT(*) FROM ratings")
                rating_count = int(cursor.fetchone()[0])
        finally:
            connection.close()
        return {"status": "ok", "ratingCount": rating_count}
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@app.post("/api/python/calculate", response_model=RecommendResponse)
def calculate(payload: RecommendRequest) -> RecommendResponse:
    try:
        recommendations = _hybrid_recommend(payload.userId, payload.topN)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    return RecommendResponse(userId=payload.userId, recommendations=recommendations)
