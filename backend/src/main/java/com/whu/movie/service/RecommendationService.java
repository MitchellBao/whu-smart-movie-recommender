package com.whu.movie.service;

import com.whu.movie.dto.PythonRecommendResponse;
import com.whu.movie.dto.RecommendationItem;
import com.whu.movie.entity.Movie;
import com.whu.movie.entity.Rating;
import com.whu.movie.entity.Recommendation;
import com.whu.movie.entity.User;
import com.whu.movie.repository.MovieRepository;
import com.whu.movie.repository.RatingRepository;
import com.whu.movie.repository.RecommendationRepository;
import com.whu.movie.repository.UserRepository;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RecommendationService {

    private final PythonAlgorithmClient pythonAlgorithmClient;
    private final RecommendationRepository recommendationRepository;
    private final MovieRepository movieRepository;
    private final RatingRepository ratingRepository;
    private final UserRepository userRepository;
    private final LlmClient llmClient;
    private final MoviePreferenceService preferenceService;

    @Value("${llm.api.explain-recommendations:false}")
    private Boolean explainRecommendationsWithLlm;

    public RecommendationService(PythonAlgorithmClient pythonAlgorithmClient,
                                 RecommendationRepository recommendationRepository,
                                 MovieRepository movieRepository,
                                 RatingRepository ratingRepository,
                                 UserRepository userRepository,
                                 LlmClient llmClient,
                                 MoviePreferenceService preferenceService) {
        this.pythonAlgorithmClient = pythonAlgorithmClient;
        this.recommendationRepository = recommendationRepository;
        this.movieRepository = movieRepository;
        this.ratingRepository = ratingRepository;
        this.userRepository = userRepository;
        this.llmClient = llmClient;
        this.preferenceService = preferenceService;
    }

    @Transactional
    public List<RecommendationItem> getRecommendationByUserId(Integer userId, Integer topN) {
        List<Recommendation> existing = recommendationRepository.findByUserIdOrderByPredictedScoreDesc(userId);
        if (existing.size() < topN) {
            refreshRecommendation(userId, topN);
            existing = recommendationRepository.findByUserIdOrderByPredictedScoreDesc(userId);
        }
        List<RecommendationItem> items = new ArrayList<>();
        Set<Integer> dislikedMovieIds = preferenceService.dislikedMovieIds(userId);
        for (Recommendation recommendation : existing.stream()
                .filter(item -> !dislikedMovieIds.contains(item.getMovieId()))
                .limit(topN)
                .toList()) {
            RecommendationItem item = new RecommendationItem();
            item.setMovieId(recommendation.getMovieId());
            item.setScore(recommendation.getPredictedScore());
            item.setReason(recommendation.getReason());

            Optional<Movie> movie = movieRepository.findById(recommendation.getMovieId());
            movie.ifPresent(value -> {
                item.setTitle(value.getTitle());
                item.setGenres(value.getGenres());
                item.setReasonPoints(buildReasonPoints(userId, value, recommendation.getPredictedScore()));
                item.setReasonTags(buildReasonTags(userId, value));
            });
            if (item.getReasonPoints() == null) {
                item.setReasonPoints(List.of("算法根据历史评分和相似偏好计算出该推荐。"));
            }
            if (item.getReasonTags() == null) {
                item.setReasonTags(List.of("算法预测"));
            }
            items.add(item);
        }
        return items;
    }

    @Transactional
    public void refreshRecommendation(Integer userId, Integer topN) {
        PythonRecommendResponse response = pythonAlgorithmClient.calculateRecommendation(userId, topN);
        if (response == null || response.getRecommendations() == null) {
            return;
        }
        recommendationRepository.deleteByUserId(userId);
        String username = userRepository.findById(userId).map(User::getUsername).orElse("用户");
        List<Recommendation> toSave = new ArrayList<>();
        Set<Integer> dislikedMovieIds = preferenceService.dislikedMovieIds(userId);
        for (PythonRecommendResponse.PythonRecommendation pyItem : response.getRecommendations()) {
            if (dislikedMovieIds.contains(pyItem.getMovieId())) {
                continue;
            }
            Recommendation recommendation = new Recommendation();
            recommendation.setUserId(userId);
            recommendation.setMovieId(pyItem.getMovieId());
            recommendation.setPredictedScore(pyItem.getScore());

            Movie movie = movieRepository.findById(pyItem.getMovieId()).orElse(null);
            String llmReason = null;
            boolean shouldExplainWithLlm = Boolean.TRUE.equals(explainRecommendationsWithLlm)
                    && movie != null;
            if (shouldExplainWithLlm) {
                llmReason = llmClient.explainRecommendation(
                        username,
                        movie.getTitle(),
                        movie.getGenres() == null ? "未知类型" : movie.getGenres(),
                        pyItem.getScore()
                );
            }

            recommendation.setReason(isUsableLlmReason(llmReason) ? llmReason : pyItem.getReason());
            toSave.add(recommendation);
        }
        recommendationRepository.saveAll(toSave);
    }

    private boolean isUsableLlmReason(String reason) {
        return reason != null
                && !reason.isBlank()
                && !reason.toLowerCase().contains("deepseek");
    }

    private List<String> buildReasonPoints(Integer userId, Movie movie, Float predictedScore) {
        List<String> points = new ArrayList<>();
        List<String> preferredGenres = topUserGenres(userId);
        List<String> movieGenres = splitGenres(movie.getGenres());
        List<String> matchedGenres = movieGenres.stream()
                .filter(preferredGenres::contains)
                .limit(3)
                .toList();
        if (!matchedGenres.isEmpty()) {
            points.add("因为你喜欢的类型包括：" + String.join(" / ", matchedGenres));
        } else if (!movieGenres.isEmpty()) {
            points.add("该片属于 " + String.join(" / ", movieGenres.stream().limit(3).toList()) + " 类型，可扩展你的观影口味");
        }

        List<Rating> movieRatings = ratingRepository.findByMovieId(movie.getMovieId());
        double average = movieRatings.stream()
                .filter(rating -> rating.getScore() != null)
                .mapToDouble(rating -> rating.getScore().doubleValue())
                .average()
                .orElse(0.0);
        if (movieRatings.size() >= 20) {
            points.add("相似用户与历史评分数据较充分，共有 " + movieRatings.size() + " 条评分可参考");
        } else if (!movieRatings.isEmpty()) {
            points.add("已有 " + movieRatings.size() + " 条历史评分参与推荐参考");
        }
        if (average >= 3.8) {
            points.add("电影历史平均评分较高，约 " + round2(average) + " 分");
        }
        if (predictedScore != null) {
            points.add("算法预测推荐分为 " + round2(predictedScore) + "，在当前候选集中排序靠前");
        }
        return points.isEmpty() ? List.of("算法根据历史评分、相似用户偏好和电影热度综合推荐。") : points;
    }

    private List<String> buildReasonTags(Integer userId, Movie movie) {
        List<String> tags = new ArrayList<>();
        List<String> preferredGenres = topUserGenres(userId);
        boolean typeMatched = splitGenres(movie.getGenres()).stream().anyMatch(preferredGenres::contains);
        if (typeMatched) {
            tags.add("类型匹配");
        }
        List<Rating> movieRatings = ratingRepository.findByMovieId(movie.getMovieId());
        if (movieRatings.size() >= 20) {
            tags.add("历史评分充分");
        }
        double average = movieRatings.stream()
                .filter(rating -> rating.getScore() != null)
                .mapToDouble(rating -> rating.getScore().doubleValue())
                .average()
                .orElse(0.0);
        if (average >= 3.8) {
            tags.add("高口碑");
        }
        if (Boolean.TRUE.equals(explainRecommendationsWithLlm)) {
            tags.add("LLM 总结");
        }
        tags.add("算法预测");
        return tags.stream().distinct().toList();
    }

    private List<String> topUserGenres(Integer userId) {
        Map<String, Integer> counts = new HashMap<>();
        for (Rating rating : ratingRepository.findByUserIdOrderByTimestampDesc(userId)) {
            if (rating.getScore() == null || rating.getScore() < 3.5f) {
                continue;
            }
            movieRepository.findById(rating.getMovieId()).ifPresent(movie -> {
                for (String genre : splitGenres(movie.getGenres())) {
                    counts.merge(genre, 1, Integer::sum);
                }
            });
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<String> splitGenres(String genres) {
        if (genres == null || genres.isBlank() || "(no genres listed)".equalsIgnoreCase(genres.trim())) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String genre : genres.split("\\|")) {
            if (!genre.isBlank()) {
                result.add(genre.trim());
            }
        }
        return result;
    }

    private double round2(Number value) {
        return Math.round(value.doubleValue() * 100.0) / 100.0;
    }
}
