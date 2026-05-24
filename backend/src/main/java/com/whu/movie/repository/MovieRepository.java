package com.whu.movie.repository;

import com.whu.movie.entity.Movie;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, Integer> {
    Page<Movie> findByTitleContainingIgnoreCaseOrGenresContainingIgnoreCase(String title, String genres, Pageable pageable);
}
