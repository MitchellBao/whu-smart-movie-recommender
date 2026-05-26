package com.whu.movie.service;

import com.whu.movie.entity.Movie;
import com.whu.movie.entity.Rating;
import com.whu.movie.entity.Recommendation;
import com.whu.movie.repository.MovieRepository;
import com.whu.movie.repository.RatingRepository;
import com.whu.movie.repository.RecommendationRepository;
import com.whu.movie.repository.UserRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class StatsService {

    private final MovieRepository movieRepository;
    private final RatingRepository ratingRepository;
    private final RecommendationRepository recommendationRepository;
    private final UserRepository userRepository;

    public StatsService(MovieRepository movieRepository,
                        RatingRepository ratingRepository,
                        RecommendationRepository recommendationRepository,
                        UserRepository userRepository) {
        this.movieRepository = movieRepository;
        this.ratingRepository = ratingRepository;
        this.recommendationRepository = recommendationRepository;
        this.userRepository = userRepository;
    }

    public Map<String, Object> overview() {
        List<Rating> ratings = ratingRepository.findAll();
        double averageScore = ratings.stream()
                .map(Rating::getScore)
                .filter(score -> score != null)
                .mapToDouble(Float::doubleValue)
                .average()
                .orElse(0.0);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("movieCount", movieRepository.count());
        data.put("userCount", userRepository.count());
        data.put("ratingCount", ratings.size());
        data.put("averageScore", round2(averageScore));
        return data;
    }

    public List<Map<String, Object>> genreDistribution() {
        Map<String, Long> counts = new HashMap<>();
        for (Movie movie : movieRepository.findAll()) {
            for (String genre : splitGenres(movie.getGenres())) {
                counts.merge(genre, 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> point(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<Map<String, Object>> topRatedMovies(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        Map<Integer, List<Rating>> grouped = ratingRepository.findAll().stream()
                .filter(rating -> rating.getMovieId() != null && rating.getScore() != null)
                .collect(Collectors.groupingBy(Rating::getMovieId));

        Map<Integer, Movie> moviesById = movieRepository.findAllById(grouped.keySet()).stream()
                .collect(Collectors.toMap(Movie::getMovieId, Function.identity()));

        return grouped.entrySet().stream()
                .map(entry -> {
                    List<Rating> ratings = entry.getValue();
                    double average = ratings.stream().mapToDouble(rating -> rating.getScore().doubleValue()).average().orElse(0.0);
                    Movie movie = moviesById.get(entry.getKey());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("movieId", entry.getKey());
                    item.put("title", movie == null ? "Movie #" + entry.getKey() : movie.getTitle());
                    item.put("ratingCount", ratings.size());
                    item.put("averageScore", round2(average));
                    return item;
                })
                .sorted(Comparator
                        .<Map<String, Object>>comparingInt(item -> ((Number) item.get("ratingCount")).intValue())
                        .reversed()
                        .thenComparing(item -> ((Number) item.get("averageScore")).doubleValue(), Comparator.reverseOrder()))
                .limit(safeLimit)
                .toList();
    }

    public Map<String, Object> userProfile(Integer userId) {
        List<Rating> ratings = ratingRepository.findByUserIdOrderByTimestampDesc(userId);
        Map<Integer, Movie> moviesById = movieRepository.findAllById(
                        ratings.stream().map(Rating::getMovieId).filter(id -> id != null).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Movie::getMovieId, Function.identity()));

        double averageScore = ratings.stream()
                .map(Rating::getScore)
                .filter(score -> score != null)
                .mapToDouble(Float::doubleValue)
                .average()
                .orElse(0.0);

        Map<String, Long> scoreBuckets = new LinkedHashMap<>();
        for (double score = 0.5; score <= 5.0; score += 0.5) {
            scoreBuckets.put(String.format("%.1f", score), 0L);
        }
        Map<String, Long> genres = new HashMap<>();
        for (Rating rating : ratings) {
            if (rating.getScore() != null) {
                scoreBuckets.computeIfPresent(String.format("%.1f", rating.getScore()), (key, value) -> value + 1);
            }
            Movie movie = moviesById.get(rating.getMovieId());
            if (movie != null) {
                for (String genre : splitGenres(movie.getGenres())) {
                    genres.merge(genre, 1L, Long::sum);
                }
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ratingCount", ratings.size());
        data.put("averageScore", round2(averageScore));
        data.put("scoreDistribution", scoreBuckets.entrySet().stream()
                .map(entry -> point(entry.getKey(), entry.getValue()))
                .toList());
        data.put("genrePreference", genres.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> point(entry.getKey(), entry.getValue()))
                .toList());
        return data;
    }

    public Map<String, Object> recommendationProfile(Integer userId) {
        List<Recommendation> recommendations = recommendationRepository.findByUserIdOrderByPredictedScoreDesc(userId);
        Map<Integer, Movie> moviesById = movieRepository.findAllById(
                        recommendations.stream().map(Recommendation::getMovieId).filter(id -> id != null).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Movie::getMovieId, Function.identity()));

        Map<String, Long> genres = new HashMap<>();
        List<Map<String, Object>> scores = new ArrayList<>();
        for (Recommendation recommendation : recommendations) {
            Movie movie = moviesById.get(recommendation.getMovieId());
            if (movie != null) {
                for (String genre : splitGenres(movie.getGenres())) {
                    genres.merge(genre, 1L, Long::sum);
                }
            }
            Map<String, Object> score = new LinkedHashMap<>();
            score.put("movieId", recommendation.getMovieId());
            score.put("title", movie == null ? "Movie #" + recommendation.getMovieId() : movie.getTitle());
            score.put("score", recommendation.getPredictedScore());
            scores.add(score);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recommendationCount", recommendations.size());
        data.put("genreDistribution", genres.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(10)
                .map(entry -> point(entry.getKey(), entry.getValue()))
                .toList());
        data.put("scoreRanking", scores.stream().limit(10).toList());
        return data;
    }

    private List<String> splitGenres(String genres) {
        if (genres == null || genres.isBlank() || "(no genres listed)".equalsIgnoreCase(genres.trim())) {
            return List.of("Unknown");
        }
        List<String> result = new ArrayList<>();
        for (String genre : genres.split("\\|")) {
            if (!genre.isBlank()) {
                result.add(genre.trim());
            }
        }
        return result.isEmpty() ? List.of("Unknown") : result;
    }

    private Map<String, Object> point(String name, Number value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("value", value);
        return item;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
