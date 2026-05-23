import argparse
import csv
import os
import re
from pathlib import Path
from urllib.parse import urlparse

import pymysql


def load_env_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        raise FileNotFoundError(f"env file not found: {path}")
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def parse_mysql_config(env: dict[str, str]) -> dict[str, object]:
    jdbc_url = env.get(
        "MYSQL_URL",
        "jdbc:mysql://127.0.0.1:3306/movie_recommender?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai",
    )
    url = jdbc_url.removeprefix("jdbc:")
    parsed = urlparse(url)
    return {
        "host": parsed.hostname or "127.0.0.1",
        "port": parsed.port or 3306,
        "database": parsed.path.lstrip("/") or "movie_recommender",
        "user": env.get("MYSQL_USER", "root"),
        "password": env.get("MYSQL_PASSWORD", "root"),
        "charset": "utf8mb4",
    }


def find_dataset_file(data_dir: Path, name: str) -> Path:
    direct = data_dir / name
    if direct.exists():
        return direct
    matches = list(data_dir.rglob(name))
    if not matches:
        raise FileNotFoundError(f"{name} not found under {data_dir}")
    return matches[0]


def extract_release_year(title: str) -> int | None:
    match = re.search(r"\((\d{4})\)\s*$", title)
    if not match:
        return None
    return int(match.group(1))


def chunks(rows: list[tuple], size: int = 1000):
    for index in range(0, len(rows), size):
        yield rows[index : index + size]


def ensure_schema(connection, database: str) -> None:
    with connection.cursor() as cursor:
        cursor.execute(
            f"CREATE DATABASE IF NOT EXISTS `{database}` "
            "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
        )
        cursor.execute(f"USE `{database}`")
        cursor.execute(
            """
            CREATE TABLE IF NOT EXISTS users (
                user_id INT PRIMARY KEY AUTO_INCREMENT,
                username VARCHAR(50) UNIQUE NOT NULL,
                password VARCHAR(255) NOT NULL,
                gender VARCHAR(10),
                age INT
            )
            """
        )
        cursor.execute(
            """
            CREATE TABLE IF NOT EXISTS movies (
                movie_id INT PRIMARY KEY,
                title VARCHAR(255) NOT NULL,
                release_year INT,
                genres VARCHAR(255),
                director VARCHAR(100)
            )
            """
        )
        cursor.execute(
            """
            CREATE TABLE IF NOT EXISTS ratings (
                rating_id INT PRIMARY KEY AUTO_INCREMENT,
                user_id INT NOT NULL,
                movie_id INT NOT NULL,
                score FLOAT NOT NULL,
                timestamp BIGINT,
                CONSTRAINT fk_rating_user FOREIGN KEY (user_id) REFERENCES users(user_id),
                CONSTRAINT fk_rating_movie FOREIGN KEY (movie_id) REFERENCES movies(movie_id)
            )
            """
        )
        cursor.execute(
            """
            CREATE TABLE IF NOT EXISTS recommendations (
                id INT PRIMARY KEY AUTO_INCREMENT,
                user_id INT NOT NULL,
                movie_id INT NOT NULL,
                predicted_score FLOAT,
                reason TEXT,
                CONSTRAINT fk_rec_user FOREIGN KEY (user_id) REFERENCES users(user_id),
                CONSTRAINT fk_rec_movie FOREIGN KEY (movie_id) REFERENCES movies(movie_id)
            )
            """
        )
    connection.commit()


def load_movies(path: Path) -> list[tuple]:
    rows = []
    with path.open("r", encoding="utf-8", newline="") as file:
        reader = csv.DictReader(file)
        for row in reader:
            title = row["title"]
            rows.append(
                (
                    int(row["movieId"]),
                    title,
                    extract_release_year(title),
                    row["genres"],
                    None,
                )
            )
    return rows


def load_ratings(path: Path) -> tuple[list[tuple], list[tuple]]:
    ratings = []
    users: set[int] = set()
    with path.open("r", encoding="utf-8", newline="") as file:
        reader = csv.DictReader(file)
        for row in reader:
            user_id = int(row["userId"])
            users.add(user_id)
            ratings.append(
                (
                    user_id,
                    int(row["movieId"]),
                    float(row["rating"]),
                    int(row["timestamp"]),
                )
            )
    user_rows = [(user_id, f"user_{user_id}", "imported", None, None) for user_id in sorted(users)]
    return user_rows, ratings


def import_dataset(connection, database: str, movies_path: Path, ratings_path: Path) -> None:
    movie_rows = load_movies(movies_path)
    user_rows, rating_rows = load_ratings(ratings_path)

    with connection.cursor() as cursor:
        cursor.execute(f"USE `{database}`")
        cursor.execute("DELETE FROM recommendations")
        cursor.execute("DELETE FROM ratings")
        cursor.execute("DELETE FROM movies")
        cursor.execute("DELETE FROM users")

        for batch in chunks(user_rows):
            cursor.executemany(
                """
                INSERT INTO users (user_id, username, password, gender, age)
                VALUES (%s, %s, %s, %s, %s)
                """,
                batch,
            )

        for batch in chunks(movie_rows):
            cursor.executemany(
                """
                INSERT INTO movies (movie_id, title, release_year, genres, director)
                VALUES (%s, %s, %s, %s, %s)
                """,
                batch,
            )

        for batch in chunks(rating_rows):
            cursor.executemany(
                """
                INSERT INTO ratings (user_id, movie_id, score, timestamp)
                VALUES (%s, %s, %s, %s)
                """,
                batch,
            )

    connection.commit()

    print(f"[OK] imported users:   {len(user_rows)}")
    print(f"[OK] imported movies:  {len(movie_rows)}")
    print(f"[OK] imported ratings: {len(rating_rows)}")
    print("[OK] cleared recommendations cache")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--data-dir", required=True)
    parser.add_argument("--env-file", required=True)
    args = parser.parse_args()

    data_dir = Path(args.data_dir).resolve()
    env_file = Path(args.env_file).resolve()
    movies_path = find_dataset_file(data_dir, "movies.csv")
    ratings_path = find_dataset_file(data_dir, "ratings.csv")

    env = load_env_file(env_file)
    config = parse_mysql_config(env)
    database = str(config.pop("database"))

    print(f"movies.csv:  {movies_path}")
    print(f"ratings.csv: {ratings_path}")
    print(f"mysql:       {config['host']}:{config['port']}/{database}")

    connection = pymysql.connect(**config, autocommit=False)
    try:
        ensure_schema(connection, database)
        import_dataset(connection, database, movies_path, ratings_path)
    finally:
        connection.close()


if __name__ == "__main__":
    main()
