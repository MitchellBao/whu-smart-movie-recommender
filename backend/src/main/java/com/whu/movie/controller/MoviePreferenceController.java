package com.whu.movie.controller;

import com.whu.movie.dto.MoviePreferenceRequest;
import com.whu.movie.service.MoviePreferenceService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/movie/preference")
public class MoviePreferenceController {

    private final MoviePreferenceService preferenceService;

    public MoviePreferenceController(MoviePreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam Integer userId,
                                    @RequestParam(required = false) String status) {
        return success(preferenceService.list(userId, status));
    }

    @PostMapping
    public Map<String, Object> set(@RequestBody MoviePreferenceRequest request) {
        return success(preferenceService.setPreference(request));
    }

    @DeleteMapping
    public Map<String, Object> remove(@RequestParam Integer userId,
                                      @RequestParam Integer movieId) {
        preferenceService.remove(userId, movieId);
        return success(true);
    }

    private Map<String, Object> success(Object data) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("data", data);
        return result;
    }
}
