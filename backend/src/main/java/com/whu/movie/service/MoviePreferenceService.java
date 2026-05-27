package com.whu.movie.service;

import com.whu.movie.dto.MoviePreferenceRequest;
import com.whu.movie.dto.UserMoviePreferenceItem;
import com.whu.movie.entity.Movie;
import com.whu.movie.entity.UserMoviePreference;
import com.whu.movie.repository.MovieRepository;
import com.whu.movie.repository.UserMoviePreferenceRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MoviePreferenceService {

    public static final String WANT = "WANT";
    public static final String FAVORITE = "FAVORITE";
    public static final String DISLIKE = "DISLIKE";

    private static final Set<String> ALLOWED_STATUS = Set.of(WANT, FAVORITE, DISLIKE);

    private final UserMoviePreferenceRepository preferenceRepository;
    private final MovieRepository movieRepository;

    public MoviePreferenceService(UserMoviePreferenceRepository preferenceRepository,
                                  MovieRepository movieRepository) {
        this.preferenceRepository = preferenceRepository;
        this.movieRepository = movieRepository;
    }

    public List<UserMoviePreferenceItem> list(Integer userId, String status) {
        String normalized = normalizeStatus(status);
        List<UserMoviePreference> preferences = normalized == null
                ? preferenceRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                : preferenceRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, normalized);
        return preferences.stream().map(this::toItem).toList();
    }

    public String findStatus(Integer userId, Integer movieId) {
        if (userId == null || movieId == null) {
            return null;
        }
        return preferenceRepository.findByUserIdAndMovieId(userId, movieId)
                .map(UserMoviePreference::getStatus)
                .orElse(null);
    }

    public Set<Integer> dislikedMovieIds(Integer userId) {
        return preferenceRepository.findByUserIdAndStatus(userId, DISLIKE).stream()
                .map(UserMoviePreference::getMovieId)
                .collect(Collectors.toSet());
    }

    @Transactional
    public UserMoviePreferenceItem setPreference(MoviePreferenceRequest request) {
        if (request.getUserId() == null || request.getMovieId() == null) {
            throw new IllegalArgumentException("userId 和 movieId 不能为空");
        }
        String status = normalizeStatus(request.getStatus());
        if (status == null) {
            throw new IllegalArgumentException("status 只能是 WANT、FAVORITE 或 DISLIKE");
        }
        movieRepository.findById(request.getMovieId())
                .orElseThrow(() -> new IllegalArgumentException("电影不存在"));

        UserMoviePreference preference = preferenceRepository
                .findByUserIdAndMovieId(request.getUserId(), request.getMovieId())
                .orElseGet(UserMoviePreference::new);
        preference.setUserId(request.getUserId());
        preference.setMovieId(request.getMovieId());
        preference.setStatus(status);
        preference.setUpdatedAt(System.currentTimeMillis());
        return toItem(preferenceRepository.save(preference));
    }

    @Transactional
    public void remove(Integer userId, Integer movieId) {
        preferenceRepository.deleteByUserIdAndMovieId(userId, movieId);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return ALLOWED_STATUS.contains(normalized) ? normalized : null;
    }

    private UserMoviePreferenceItem toItem(UserMoviePreference preference) {
        UserMoviePreferenceItem item = new UserMoviePreferenceItem();
        item.setMovieId(preference.getMovieId());
        item.setStatus(preference.getStatus());
        item.setUpdatedAt(preference.getUpdatedAt());
        Movie movie = movieRepository.findById(preference.getMovieId()).orElse(null);
        if (movie != null) {
            item.setTitle(movie.getTitle());
            item.setReleaseYear(movie.getReleaseYear());
            item.setGenres(movie.getGenres());
        } else {
            item.setTitle("Movie #" + preference.getMovieId());
        }
        return item;
    }
}
