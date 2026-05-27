package com.whu.movie.dto;

import java.util.List;

public class RecommendationItem {
    private Integer movieId;
    private String title;
    private String genres;
    private Float score;
    private String reason;
    private List<String> reasonPoints;
    private List<String> reasonTags;

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

    public String getGenres() {
        return genres;
    }

    public void setGenres(String genres) {
        this.genres = genres;
    }

    public Float getScore() {
        return score;
    }

    public void setScore(Float score) {
        this.score = score;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public List<String> getReasonPoints() {
        return reasonPoints;
    }

    public void setReasonPoints(List<String> reasonPoints) {
        this.reasonPoints = reasonPoints;
    }

    public List<String> getReasonTags() {
        return reasonTags;
    }

    public void setReasonTags(List<String> reasonTags) {
        this.reasonTags = reasonTags;
    }
}
