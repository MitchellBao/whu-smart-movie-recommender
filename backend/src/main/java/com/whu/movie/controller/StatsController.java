package com.whu.movie.controller;

import com.whu.movie.service.StatsService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        return success(statsService.overview());
    }

    @GetMapping("/genres")
    public Map<String, Object> genres() {
        return success(statsService.genreDistribution());
    }

    @GetMapping("/top-rated")
    public Map<String, Object> topRated(@RequestParam(defaultValue = "10") Integer limit) {
        return success(statsService.topRatedMovies(limit));
    }

    @GetMapping("/user")
    public Map<String, Object> user(@RequestParam Integer userId) {
        return success(statsService.userProfile(userId));
    }

    @GetMapping("/recommendation")
    public Map<String, Object> recommendation(@RequestParam Integer userId) {
        return success(statsService.recommendationProfile(userId));
    }

    private Map<String, Object> success(Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("data", data);
        return result;
    }
}
