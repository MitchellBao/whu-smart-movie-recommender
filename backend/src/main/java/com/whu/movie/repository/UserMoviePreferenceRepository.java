package com.whu.movie.repository;

import com.whu.movie.entity.UserMoviePreference;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMoviePreferenceRepository extends JpaRepository<UserMoviePreference, Integer> {

    Optional<UserMoviePreference> findByUserIdAndMovieId(Integer userId, Integer movieId);

    List<UserMoviePreference> findByUserIdOrderByUpdatedAtDesc(Integer userId);

    List<UserMoviePreference> findByUserIdAndStatusOrderByUpdatedAtDesc(Integer userId, String status);

    List<UserMoviePreference> findByUserIdAndStatus(Integer userId, String status);

    void deleteByUserIdAndMovieId(Integer userId, Integer movieId);
}
