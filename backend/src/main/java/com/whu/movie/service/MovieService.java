package com.whu.movie.service;

import com.whu.movie.dto.MovieItem;
import com.whu.movie.dto.MoviePageResponse;
import com.whu.movie.entity.Movie;
import com.whu.movie.repository.MovieRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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

    public List<MovieItem> suggest(String keyword, int limit) {
        return search(keyword, limit);
    }

    public MoviePageResponse searchPage(String keyword, String initial, String genre, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(5, Math.min(pageSize, 30));

        List<Movie> filtered = movieRepository.findAll().stream()
                .filter(movie -> matchesKeyword(movie, keyword))
                .filter(movie -> matchesInitial(movie, initial))
                .filter(movie -> matchesGenre(movie, genre))
                .sorted(Comparator.comparing(Movie::getTitle, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int totalItems = filtered.size();
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil(totalItems / (double) safePageSize);
        int normalizedPage = totalPages == 0 ? 1 : Math.min(safePage, totalPages);
        int fromIndex = totalItems == 0 ? 0 : (normalizedPage - 1) * safePageSize;
        int toIndex = Math.min(fromIndex + safePageSize, totalItems);

        MoviePageResponse response = new MoviePageResponse();
        response.setItems(totalItems == 0 ? List.of() : filtered.subList(fromIndex, toIndex).stream().map(this::toItem).toList());
        response.setPage(normalizedPage);
        response.setPageSize(safePageSize);
        response.setTotalPages(totalPages);
        response.setTotalItems(totalItems);
        return response;
    }

    public List<String> listGenres() {
        return movieRepository.findAll().stream()
                .flatMap(movie -> splitGenres(movie.getGenres()).stream())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private boolean matchesKeyword(Movie movie, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String term = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(movie.getTitle(), term) || contains(movie.getGenres(), term);
    }

    private boolean matchesInitial(Movie movie, String initial) {
        if (initial == null || initial.isBlank() || "all".equalsIgnoreCase(initial)) {
            return true;
        }
        String normalized = initial.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]")) {
            return true;
        }
        String title = normalizeSortableTitle(movie.getTitle());
        return !title.isBlank() && title.substring(0, 1).toUpperCase(Locale.ROOT).equals(normalized);
    }

    private boolean matchesGenre(Movie movie, String genre) {
        if (genre == null || genre.isBlank() || "all".equalsIgnoreCase(genre)) {
            return true;
        }
        String normalized = genre.trim().toLowerCase(Locale.ROOT);
        return splitGenres(movie.getGenres()).stream()
                .anyMatch(item -> item.toLowerCase(Locale.ROOT).equals(normalized));
    }

    private boolean contains(String text, String term) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(term);
    }

    private String normalizeSortableTitle(String title) {
        if (title == null) {
            return "";
        }
        String trimmed = title.trim();
        if (trimmed.toLowerCase(Locale.ROOT).endsWith(", the")) {
            return "The " + trimmed.substring(0, trimmed.length() - 5).trim();
        }
        if (trimmed.toLowerCase(Locale.ROOT).endsWith(", a")) {
            return "A " + trimmed.substring(0, trimmed.length() - 3).trim();
        }
        return trimmed;
    }

    private List<String> splitGenres(String genres) {
        if (genres == null || genres.isBlank() || "(no genres listed)".equalsIgnoreCase(genres.trim())) {
            return List.of("Unknown");
        }
        List<String> result = new ArrayList<>();
        for (String genre : genres.split("\\|")) {
            if (!genre.isBlank()) {
                result.add(genre.trim());
            }
        }
        return result.isEmpty() ? List.of("Unknown") : result;
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
