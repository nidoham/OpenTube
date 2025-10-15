package com.nidoham.newpipe.stream;

import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced Search Query Extractor with pagination support
 * Handles YouTube searches and provides functionality to fetch more results
 */
public class SearchQueryExtractor {
    private final StreamingService service;
    private final String searchQuery;
    private SearchExtractor newPipeSearchExtractor;
    private Page nextPage;
    private boolean hasMoreResults = true;
    private int currentPageNumber = 0;
    private int totalResults = 0;
    private SearchCallback callback;

    public SearchQueryExtractor(StreamingService service, String searchQuery) {
        this.service = service;
        this.searchQuery = searchQuery;
    }

    public void setCallback(SearchCallback callback) {
        this.callback = callback;
    }

    /**
     * Perform initial search and fetch first page of results
     */
    public void performInitialSearch() {
        if (callback == null) {
            throw new IllegalStateException("SearchCallback must be set before performing search");
        }

        new Thread(() -> {
            try {
                callback.onSearchStarted(searchQuery);
                
                // Get NewPipe's SearchExtractor
                newPipeSearchExtractor = service.getSearchExtractor(searchQuery);
                
                // Fetch the search page using the extractor - this returns void
                newPipeSearchExtractor.fetchPage();
                
                // Get initial page from the extractor after fetching
                ListExtractor.InfoItemsPage<InfoItem> initialPage = newPipeSearchExtractor.getInitialPage();
                this.nextPage = initialPage.getNextPage();
                this.hasMoreResults = nextPage != null;
                this.currentPageNumber = 1;
                
                List<StreamInfoItem> streamItems = filterStreamItems(initialPage.getItems());
                this.totalResults = streamItems.size();
                
                SearchResult result = new SearchResult(
                    streamItems,
                    initialPage.getItems(),
                    currentPageNumber,
                    hasMoreResults,
                    totalResults,
                    null
                );
                
                callback.onSearchCompleted(result);
                
            } catch (Exception e) {
                callback.onSearchError(e);
            }
        }).start();
    }

    /**
     * Fetch next page of search results
     */
    public void fetchNextPage() {
        if (callback == null) {
            throw new IllegalStateException("SearchCallback must be set before fetching results");
        }

        if (!hasMoreResults || nextPage == null) {
            callback.onSearchError(new IllegalStateException("No more results available"));
            return;
        }

        new Thread(() -> {
            try {
                callback.onPageLoadStarted(currentPageNumber + 1);
                
                // Fetch next page using NewPipe's SearchExtractor
                ListExtractor.InfoItemsPage<InfoItem> nextItemsPage = newPipeSearchExtractor.getPage(nextPage);
                this.nextPage = nextItemsPage.getNextPage();
                this.hasMoreResults = nextPage != null;
                this.currentPageNumber++;
                
                List<StreamInfoItem> streamItems = filterStreamItems(nextItemsPage.getItems());
                this.totalResults += streamItems.size();
                
                SearchResult result = new SearchResult(
                    streamItems,
                    nextItemsPage.getItems(),
                    currentPageNumber,
                    hasMoreResults,
                    totalResults,
                    nextPage
                );
                
                callback.onPageLoadCompleted(result);
                
            } catch (Exception e) {
                callback.onSearchError(e);
            }
        }).start();
    }

    /**
     * Fetch a specific page of results (not recommended for large page numbers)
     */
    public void fetchPage(int pageNumber) {
        if (callback == null) {
            throw new IllegalStateException("SearchCallback must be set before fetching results");
        }

        if (pageNumber <= currentPageNumber) {
            callback.onSearchError(new IllegalArgumentException("Cannot fetch previous pages. Use fetchNextPage() for sequential access."));
            return;
        }
        
        new Thread(() -> {
            try {
                callback.onPageLoadStarted(pageNumber);
                
                // Fetch pages sequentially until we reach the desired page
                SearchResult result = null;
                while (currentPageNumber < pageNumber && hasMoreResults) {
                    ListExtractor.InfoItemsPage<InfoItem> nextItemsPage = newPipeSearchExtractor.getPage(nextPage);
                    this.nextPage = nextItemsPage.getNextPage();
                    this.hasMoreResults = nextPage != null;
                    this.currentPageNumber++;
                    
                    List<StreamInfoItem> streamItems = filterStreamItems(nextItemsPage.getItems());
                    this.totalResults += streamItems.size();
                    
                    result = new SearchResult(
                        streamItems,
                        nextItemsPage.getItems(),
                        currentPageNumber,
                        hasMoreResults,
                        totalResults,
                        nextPage
                    );
                }
                
                if (result != null && currentPageNumber == pageNumber) {
                    callback.onPageLoadCompleted(result);
                } else {
                    callback.onSearchError(new IllegalArgumentException("Page " + pageNumber + " is not available. Only " + currentPageNumber + " pages available."));
                }
                
            } catch (Exception e) {
                callback.onSearchError(e);
            }
        }).start();
    }

    /**
     * Filter only StreamInfoItems from the results
     */
    private List<StreamInfoItem> filterStreamItems(List<InfoItem> allItems) {
        List<StreamInfoItem> streamItems = new ArrayList<>();
        for (InfoItem item : allItems) {
            if (item instanceof StreamInfoItem) {
                streamItems.add((StreamInfoItem) item);
            }
        }
        return streamItems;
    }

    /**
     * Check if more results are available
     */
    public boolean hasMoreResults() {
        return hasMoreResults;
    }

    /**
     * Get current page number
     */
    public int getCurrentPageNumber() {
        return currentPageNumber;
    }

    /**
     * Get total results fetched so far
     */
    public int getTotalResults() {
        return totalResults;
    }

    /**
     * Reset the extractor to start from beginning
     */
    public void reset() {
        this.nextPage = null;
        this.hasMoreResults = true;
        this.currentPageNumber = 0;
        this.totalResults = 0;
        this.newPipeSearchExtractor = null;
    }

    /**
     * Search result container class
     */
    public static class SearchResult {
        private final List<StreamInfoItem> streamItems;
        private final List<InfoItem> allItems;
        private final int pageNumber;
        private final boolean hasMorePages;
        private final int totalResultsSoFar;
        private final Page nextPage;

        public SearchResult(List<StreamInfoItem> streamItems, List<InfoItem> allItems, 
                           int pageNumber, boolean hasMorePages, int totalResultsSoFar, Page nextPage) {
            this.streamItems = streamItems;
            this.allItems = allItems;
            this.pageNumber = pageNumber;
            this.hasMorePages = hasMorePages;
            this.totalResultsSoFar = totalResultsSoFar;
            this.nextPage = nextPage;
        }

        // Getters
        public List<StreamInfoItem> getStreamItems() { return streamItems; }
        public List<InfoItem> getAllItems() { return allItems; }
        public int getPageNumber() { return pageNumber; }
        public boolean hasMorePages() { return hasMorePages; }
        public int getTotalResultsSoFar() { return totalResultsSoFar; }
        public int getStreamItemsCount() { return streamItems.size(); }
        public int getAllItemsCount() { return allItems.size(); }
        public Page getNextPage() { return nextPage; }
    }
}