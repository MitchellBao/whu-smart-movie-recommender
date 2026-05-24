package com.whu.movie.controller;

import com.whu.movie.dto.MovieItem;
import com.whu.movie.dto.MoviePageResponse;
import com.whu.movie.service.MovieService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/movie")
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam(defaultValue = "") String keyword,
                                      @RequestParam(defaultValue = "12") Integer limit) {
        List<MovieItem> movies = movieService.search(keyword, limit);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("data", movies);
        return result;
    }

    @GetMapping("/page")
    public Map<String, Object> page(@RequestParam(defaultValue = "") String keyword,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "12") Integer pageSize) {
        MoviePageResponse movies = movieService.searchPage(keyword, page, pageSize);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("data", movies);
        return result;
    }
}
