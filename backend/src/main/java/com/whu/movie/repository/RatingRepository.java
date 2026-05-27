package com.whu.movie.repository;

import com.whu.movie.entity.Rating;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingRepository extends JpaRepository<Rating, Integer> {
    Optional<Rating> findTopByUserIdAndMovieIdOrderByTimestampDesc(Integer userId, Integer movieId);

    List<Rating> findByUserIdOrderByTimestampDesc(Integer userId);

    List<Rating> findByMovieId(Integer movieId);

    void deleteByUserIdAndMovieId(Integer userId, Integer movieId);
}
