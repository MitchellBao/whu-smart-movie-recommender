package com.whu.movie.service;

import com.whu.movie.dto.MovieItem;
import com.whu.movie.dto.MoviePageResponse;
import com.whu.movie.entity.Movie;
import com.whu.movie.repository.MovieRepository;
import java.util.List;
import org.springframework.data.domain.Page;
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

    public MoviePageResponse searchPage(String keyword, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(5, Math.min(pageSize, 30));
        Pageable pageable = PageRequest.of(safePage - 1, safePageSize);
        Page<Movie> moviePage;
        if (keyword == null || keyword.isBlank()) {
            moviePage = movieRepository.findAll(pageable);
        } else {
            String term = keyword.trim();
            moviePage = movieRepository.findByTitleContainingIgnoreCaseOrGenresContainingIgnoreCase(term, term, pageable);
        }

        MoviePageResponse response = new MoviePageResponse();
        response.setItems(moviePage.stream().map(this::toItem).toList());
        response.setPage(safePage);
        response.setPageSize(safePageSize);
        response.setTotalPages(moviePage.getTotalPages());
        response.setTotalItems(moviePage.getTotalElements());
        return response;
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
