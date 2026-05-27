package com.whu.movie.dto;

import java.util.List;
import java.util.Map;

public class MovieDetail {

    private Integer movieId;
    private String title;
    private Integer releaseYear;
    private String genres;
    private Double averageScore;
    private Integer ratingCount;
    private Boolean ratedByCurrentUser;
    private Float userScore;
    private String preferenceStatus;
    private List<Map<String, Object>> scoreDistribution;
    private List<SimilarMovieItem> similarMovies;

    public Integer getMovieId() {
        return movieId;
    }

    public void setMovieId(Integer movieId) {
        this.movieId = movieId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getReleaseYear() {
        return releaseYear;
    }

    public void setReleaseYear(Integer releaseYear) {
        this.releaseYear = releaseYear;
    }

    public String getGenres() {
        return genres;
    }

    public void setGenres(String genres) {
        this.genres = genres;
    }

    public Double getAverageScore() {
        return averageScore;
    }

    public void setAverageScore(Double averageScore) {
        this.averageScore = averageScore;
    }

    public Integer getRatingCount() {
        return ratingCount;
    }

    public void setRatingCount(Integer ratingCount) {
        this.ratingCount = ratingCount;
    }

    public Boolean getRatedByCurrentUser() {
        return ratedByCurrentUser;
    }

    public void setRatedByCurrentUser(Boolean ratedByCurrentUser) {
        this.ratedByCurrentUser = ratedByCurrentUser;
    }

    public Float getUserScore() {
        return userScore;
    }

    public void setUserScore(Float userScore) {
        this.userScore = userScore;
    }

    public String getPreferenceStatus() {
        return preferenceStatus;
    }

    public void setPreferenceStatus(String preferenceStatus) {
        this.preferenceStatus = preferenceStatus;
    }

    public List<Map<String, Object>> getScoreDistribution() {
        return scoreDistribution;
    }

    public void setScoreDistribution(List<Map<String, Object>> scoreDistribution) {
        this.scoreDistribution = scoreDistribution;
    }

    public List<SimilarMovieItem> getSimilarMovies() {
        return similarMovies;
    }

    public void setSimilarMovies(List<SimilarMovieItem> similarMovies) {
        this.similarMovies = similarMovies;
    }
}
