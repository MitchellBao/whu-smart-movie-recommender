package com.whu.movie.controller;

import com.whu.movie.dto.RatingSubmitRequest;
import com.whu.movie.dto.UserRatingItem;
import com.whu.movie.service.RatingService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rating")
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping("/submit")
    public Map<String, Object> submit(@Valid @RequestBody RatingSubmitRequest request) {
        ratingService.submitRating(request);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("message", "ok");
        return result;
    }

    @GetMapping("/user")
    public Map<String, Object> listUserRatings(@RequestParam Integer userId) {
        List<UserRatingItem> ratings = ratingService.listUserRatings(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("data", ratings);
        return result;
    }
}
