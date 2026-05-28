package com.whu.movie.service;

import com.whu.movie.dto.LlmQueryResponse;
import com.whu.movie.dto.LlmStatusResponse;
import com.whu.movie.dto.RecommendationItem;
import com.whu.movie.dto.UserMoviePreferenceItem;
import com.whu.movie.dto.UserRatingItem;
import com.whu.movie.entity.User;
import com.whu.movie.repository.UserRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LlmService {

    private final LlmClient llmClient;
    private final RecommendationService recommendationService;
    private final RatingService ratingService;
    private final MoviePreferenceService preferenceService;
    private final UserRepository userRepository;

    public LlmService(LlmClient llmClient,
                      RecommendationService recommendationService,
                      RatingService ratingService,
                      MoviePreferenceService preferenceService,
                      UserRepository userRepository) {
        this.llmClient = llmClient;
        this.recommendationService = recommendationService;
        this.ratingService = ratingService;
        this.preferenceService = preferenceService;
        this.userRepository = userRepository;
    }

    public LlmQueryResponse query(Integer userId, String queryText, Integer topN) {
        int safeTopN = topN == null ? 5 : Math.max(1, Math.min(topN, 20));
        List<RecommendationItem> related = recommendationService.getRecommendationByUserId(userId, safeTopN);
        String username = userRepository.findById(userId).map(User::getUsername).orElse("用户");
        List<UserRatingItem> ratings = ratingService.listUserRatings(userId);
        List<UserMoviePreferenceItem> preferences = preferenceService.list(userId, null);

        String answer = llmClient.answerUserQuery(username, queryText, buildUserContext(related, ratings, preferences));
        LlmQueryResponse response = new LlmQueryResponse();
        response.setCode(0);
        response.setResponseText(answer);
        response.setRelatedMovies(related);
        return response;
    }

    public LlmStatusResponse status() {
        return llmClient.status();
    }

    private String buildUserContext(List<RecommendationItem> recommendations,
                                    List<UserRatingItem> ratings,
                                    List<UserMoviePreferenceItem> preferences) {
        StringBuilder builder = new StringBuilder();
        builder.append("【当前推荐列表，按排名】\n");
        for (int i = 0; i < recommendations.size(); i++) {
            RecommendationItem item = recommendations.get(i);
            builder.append(i + 1)
                    .append(". 《").append(titleOf(item.getTitle(), item.getMovieId())).append("》")
                    .append("，类型：").append(emptyAsUnknown(item.getGenres()))
                    .append("，推荐分：").append(item.getScore() == null ? "未知" : String.format("%.2f", item.getScore()))
                    .append("，标签：").append(item.getReasonTags() == null ? "无" : String.join("/", item.getReasonTags()))
                    .append("\n");
        }

        builder.append("【用户评分记录摘要】\n");
        if (ratings.isEmpty()) {
            builder.append("暂无评分记录。\n");
        } else {
            double average = ratings.stream()
                    .filter(item -> item.getScore() != null)
                    .mapToDouble(item -> item.getScore().doubleValue())
                    .average()
                    .orElse(0.0);
            builder.append("评分总数：").append(ratings.size())
                    .append("，平均评分：").append(String.format("%.2f", average)).append("\n");
            builder.append("高分电影：");
            appendRatings(builder, ratings.stream()
                    .filter(item -> item.getScore() != null && item.getScore() >= 4.0f)
                    .sorted(Comparator.comparing(UserRatingItem::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(6)
                    .toList());
            builder.append("低分电影：");
            appendRatings(builder, ratings.stream()
                    .filter(item -> item.getScore() != null && item.getScore() <= 2.0f)
                    .sorted(Comparator.comparing(UserRatingItem::getScore, Comparator.nullsLast(Comparator.naturalOrder())))
                    .limit(4)
                    .toList());
        }

        builder.append("【显式偏好】\n");
        appendPreferenceGroup(builder, "想看", preferences, MoviePreferenceService.WANT);
        appendPreferenceGroup(builder, "收藏", preferences, MoviePreferenceService.FAVORITE);
        appendPreferenceGroup(builder, "不感兴趣", preferences, MoviePreferenceService.DISLIKE);
        return builder.toString();
    }

    private void appendRatings(StringBuilder builder, List<UserRatingItem> ratings) {
        if (ratings.isEmpty()) {
            builder.append("无\n");
            return;
        }
        builder.append(String.join("、", ratings.stream()
                .map(item -> "《" + titleOf(item.getTitle(), item.getMovieId()) + "》" + item.getScore() + "分")
                .toList()));
        builder.append("\n");
    }

    private void appendPreferenceGroup(StringBuilder builder,
                                       String label,
                                       List<UserMoviePreferenceItem> preferences,
                                       String status) {
        List<String> titles = preferences.stream()
                .filter(item -> status.equals(item.getStatus()))
                .limit(8)
                .map(item -> "《" + titleOf(item.getTitle(), item.getMovieId()) + "》")
                .toList();
        builder.append(label).append("：")
                .append(titles.isEmpty() ? "无" : String.join("、", titles))
                .append("\n");
    }

    private String titleOf(String title, Integer movieId) {
        return title == null || title.isBlank() ? "movie-" + movieId : title;
    }

    private String emptyAsUnknown(String value) {
        return value == null || value.isBlank() ? "未知" : value;
    }
}
