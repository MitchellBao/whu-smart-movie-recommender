package com.whu.movie.dto;

import java.util.ArrayList;
import java.util.List;

public class MoviePageResponse {

    private List<MovieItem> items = new ArrayList<>();
    private int page;
    private int pageSize;
    private int totalPages;
    private long totalItems;

    public List<MovieItem> getItems() {
        return items;
    }

    public void setItems(List<MovieItem> items) {
        this.items = items;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public long getTotalItems() {
        return totalItems;
    }

    public void setTotalItems(long totalItems) {
        this.totalItems = totalItems;
    }
}
