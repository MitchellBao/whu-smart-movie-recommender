package com.whu.movie.service;

import com.whu.movie.dto.MovieItem;
import com.whu.movie.entity.Movie;
import com.whu.movie.repository.MovieRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class MovieService {

    private final MovieRepository movieRepository;

    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    public List<MovieItem> search(String keyword, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        Pageable pageable = PageRequest.of(0, safeLimit);
        if (keyword == null || keyword.isBlank()) {
            return movieRepository.findAll(pageable).stream().map(this::toItem).toList();
        }
        String term = keyword.trim();
        return movieRepository
                .findByTitleContainingIgnoreCaseOrGenresContainingIgnoreCase(term, term, pageable)
                .stream()
                .map(this::toItem)
                .toList();
    }

    private MovieItem toItem(Movie movie) {
        MovieItem item = new MovieItem();
        item.setMovieId(movie.getMovieId());
        item.setTitle(movie.getTitle());
        item.setReleaseYear(movie.getReleaseYear());
        item.setGenres(movie.getGenres());
        return item;
    }
}
