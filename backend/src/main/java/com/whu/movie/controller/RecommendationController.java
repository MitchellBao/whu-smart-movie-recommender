package com.whu.movie.controller;

import com.whu.movie.dto.RecommendationItem;
import com.whu.movie.service.RecommendationRefreshService;
import com.whu.movie.service.RecommendationService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommend")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final RecommendationRefreshService recommendationRefreshService;

    public RecommendationController(RecommendationService recommendationService,
                                    RecommendationRefreshService recommendationRefreshService) {
        this.recommendationService = recommendationService;
        this.recommendationRefreshService = recommendationRefreshService;
    }

    @GetMapping("/movie")
    public Map<String, Object> recommend(@RequestParam Integer userId,
                                         @RequestParam(defaultValue = "10") Integer topN) {
        List<RecommendationItem> items = recommendationService.getRecommendationByUserId(userId, topN);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("data", items);
        return result;
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh(@RequestParam Integer userId,
                                       @RequestParam(defaultValue = "10") Integer topN) {
        recommendationRefreshService.refreshAsync(userId, topN);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("refreshing", true);
        return result;
    }

    @GetMapping("/refresh/status")
    public Map<String, Object> refreshStatus(@RequestParam Integer userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("refreshing", recommendationRefreshService.isRefreshing(userId));
        return result;
    }
}
