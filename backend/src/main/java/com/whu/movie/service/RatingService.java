package com.whu.movie.service;

import com.whu.movie.dto.RatingSubmitRequest;
import com.whu.movie.dto.UserRatingItem;
import com.whu.movie.entity.Movie;
import com.whu.movie.entity.Rating;
import com.whu.movie.repository.MovieRepository;
import com.whu.movie.repository.RatingRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RatingService {

    private final RatingRepository ratingRepository;
    private final MovieRepository movieRepository;
    private final RecommendationService recommendationService;

    public RatingService(RatingRepository ratingRepository,
                         MovieRepository movieRepository,
                         RecommendationService recommendationService) {
        this.ratingRepository = ratingRepository;
        this.movieRepository = movieRepository;
        this.recommendationService = recommendationService;
    }

    public void submitRating(RatingSubmitRequest request) {
        validateRatingScore(request.getScore());

        Rating rating = ratingRepository
                .findTopByUserIdAndMovieIdOrderByTimestampDesc(request.getUserId(), request.getMovieId())
                .orElseGet(Rating::new);
        if (rating.getRatingId() == null) {
            rating.setUserId(request.getUserId());
            rating.setMovieId(request.getMovieId());
        }
        rating.setScore(request.getScore());
        rating.setTimestamp(System.currentTimeMillis() / 1000);
        ratingRepository.save(rating);

        // 评分变化后同步触发推荐更新。
        recommendationService.refreshRecommendation(request.getUserId(), 10);
    }

    public List<UserRatingItem> listUserRatings(Integer userId) {
        List<Rating> ratings = ratingRepository.findByUserIdOrderByTimestampDesc(userId);
        List<UserRatingItem> items = new ArrayList<>();
        Set<Integer> seenMovies = new LinkedHashSet<>();
        for (Rating rating : ratings) {
            if (!seenMovies.add(rating.getMovieId())) {
                continue;
            }
            UserRatingItem item = new UserRatingItem();
            item.setRatingId(rating.getRatingId());
            item.setMovieId(rating.getMovieId());
            item.setScore(rating.getScore());
            item.setTimestamp(rating.getTimestamp());
            Movie movie = movieRepository.findById(rating.getMovieId()).orElse(null);
            if (movie != null) {
                item.setTitle(movie.getTitle());
                item.setGenres(movie.getGenres());
            }
            items.add(item);
        }
        return items;
    }

    private void validateRatingScore(Float score) {
        if (score == null) {
            throw new IllegalArgumentException("评分不能为空");
        }
        if (score < 0.5f || score > 5.0f) {
            throw new IllegalArgumentException("评分必须在 0.5 到 5.0 之间");
        }
        float doubled = score * 2.0f;
        if (Math.abs(doubled - Math.round(doubled)) > 0.0001f) {
            throw new IllegalArgumentException("评分只能以 0.5 为步长");
        }
    }
}
