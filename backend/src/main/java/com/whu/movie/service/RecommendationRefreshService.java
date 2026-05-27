package com.whu.movie.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class RecommendationRefreshService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationRefreshService.class);

    private final RecommendationService recommendationService;
    private final Map<Integer, Boolean> refreshingByUser = new ConcurrentHashMap<>();

    public RecommendationRefreshService(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    public boolean isRefreshing(Integer userId) {
        return Boolean.TRUE.equals(refreshingByUser.get(userId));
    }

    @Async
    public void refreshAsync(Integer userId, Integer topN) {
        if (userId == null) {
            return;
        }
        if (isRefreshing(userId)) {
            return;
        }
        refreshingByUser.put(userId, true);
        try {
            recommendationService.refreshRecommendation(userId, topN == null ? 10 : topN);
        } catch (Exception ex) {
            log.error("Async recommendation refresh failed. userId={}, topN={}, error={}", userId, topN, ex.toString());
        } finally {
            refreshingByUser.put(userId, false);
        }
    }
}
