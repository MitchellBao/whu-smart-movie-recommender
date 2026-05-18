CREATE DATABASE IF NOT EXISTS movie_recommender;
USE movie_recommender;

CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE movies (
    id BIGINT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    genres VARCHAR(255)
);

CREATE TABLE ratings (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    movie_id BIGINT NOT NULL,
    rating DOUBLE NOT NULL,
    timestamp BIGINT
);

CREATE TABLE recommendations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    movie_id BIGINT NOT NULL,
    predicted_score DOUBLE,
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);