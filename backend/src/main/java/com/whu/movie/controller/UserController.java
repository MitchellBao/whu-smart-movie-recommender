package com.whu.movie.controller;

import com.whu.movie.dto.AuthRequest;
import com.whu.movie.dto.UserProfile;
import com.whu.movie.service.UserService;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody AuthRequest request) {
        return success(userService.register(request));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody AuthRequest request) {
        return success(userService.login(request));
    }

    private Map<String, Object> success(UserProfile profile) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("data", profile);
        return result;
    }
}
