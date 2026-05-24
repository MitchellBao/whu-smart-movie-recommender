package com.whu.movie.service;

import com.whu.movie.dto.AuthRequest;
import com.whu.movie.dto.UserProfile;
import com.whu.movie.entity.User;
import com.whu.movie.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserProfile register(AuthRequest request) {
        String username = request.getUsername().trim();
        userRepository.findByUsername(username).ifPresent(user -> {
            throw new IllegalArgumentException("用户名已存在");
        });

        User user = new User();
        user.setUsername(username);
        user.setPassword(request.getPassword());
        user.setAge(request.getAge());
        user.setGender(request.getGender());
        return toProfile(userRepository.save(user));
    }

    public UserProfile login(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername().trim())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (!user.getPassword().equals(request.getPassword())) {
            throw new IllegalArgumentException("密码错误");
        }
        return toProfile(user);
    }

    private UserProfile toProfile(User user) {
        UserProfile profile = new UserProfile();
        profile.setUserId(user.getUserId());
        profile.setUsername(user.getUsername());
        profile.setAge(user.getAge());
        profile.setGender(user.getGender());
        return profile;
    }
}
