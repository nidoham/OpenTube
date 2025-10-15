package com.nidoham.stream.data;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * A service that wraps NewPipe's search functionality with RxJava.
 * This class is STATEFUL and is a direct RxJava replacement for the original
 * SearchQueryExtractor. A new instance should be created for each new search query.
 */
public class SearchService {
    private final StreamingService service;
    private final String searchQuery;

    // --- State variables from your original, working class ---
    private SearchExtractor newPipeSearchExtractor;
    private Page nextPage;
    private boolean hasMoreResults = true;
    private int currentPageNumber = 0;
    private int totalResults = 0;

    public SearchService(StreamingService service, String searchQuery) {
        this.service = service;
        this.searchQuery = searchQuery;
    }

    /**
     * Performs the initial search and fetches the first page of results.
     * This is the RxJava equivalent of your performInitialSearch() method.
     */
    public Single<SearchResult> performInitialSearch() {
        return Single.<SearchResult>create(emitter -> {
            try {
                // Get NewPipe's SearchExtractor
                newPipeSearchExtractor = service.getSearchExtractor(searchQuery);
                newPipeSearchExtractor.fetchPage();

                // Get initial page from the extractor after fetching
                ListExtractor.InfoItemsPage<InfoItem> initialPage = newPipeSearchExtractor.getInitialPage();
                this.nextPage = initialPage.getNextPage();
                this.hasMoreResults = this.nextPage != null;
                this.currentPageNumber = 1;

                List<StreamInfoItem> streamItems = filterStreamItems(initialPage.getItems());
                this.totalResults = streamItems.size();

                SearchResult result = new SearchResult(
                        streamItems,
                        initialPage.getItems(),
                        currentPageNumber,
                        hasMoreResults,
                        totalResults
                );

                // Instead of callback.onSearchCompleted(result), we emit success.
                if (!emitter.isDisposed()) {
                    emitter.onSuccess(result);
                }

            } catch (Exception e) {
                // Instead of callback.onSearchError(e), we emit an error.
                if (!emitter.isDisposed()) {
                    emitter.onError(e);
                }
            }
        }).subscribeOn(Schedulers.io()); // Ensure the work runs on a background thread.
    }

    /**
     * Fetches the next page of search results.
     * This is the RxJava equivalent of your fetchNextPage() method.
     */
    public Single<SearchResult> fetchNextPage() {
        return Single.<SearchResult>create(emitter -> {
            if (!this.hasMoreResults || this.nextPage == null) {
                if (!emitter.isDisposed()) {
                    emitter.onError(new IllegalStateException("No more results available"));
                }
                return;
            }

            try {
                // Fetch next page using NewPipe's SearchExtractor
                ListExtractor.InfoItemsPage<InfoItem> nextItemsPage = newPipeSearchExtractor.getPage(this.nextPage);
                this.nextPage = nextItemsPage.getNextPage();
                this.hasMoreResults = this.nextPage != null;
                this.currentPageNumber++;

                List<StreamInfoItem> streamItems = filterStreamItems(nextItemsPage.getItems());
                this.totalResults += streamItems.size();

                SearchResult result = new SearchResult(
                        streamItems,
                        nextItemsPage.getItems(),
                        currentPageNumber,
                        hasMoreResults,
                        totalResults
                );

                // Instead of callback.onPageLoadCompleted(result), we emit success.
                if (!emitter.isDisposed()) {
                    emitter.onSuccess(result);
                }

            } catch (Exception e) {
                // Instead of callback.onSearchError(e), we emit an error.
                if (!emitter.isDisposed()) {
                    emitter.onError(e);
                }
            }
        }).subscribeOn(Schedulers.io());
    }

    /**
     * A helper method from your original class to check if more results are available.
     */
    public boolean hasMoreResults() {
        return hasMoreResults;
    }

    /**
     * A helper method from your original class to filter results.
     */
    private List<StreamInfoItem> filterStreamItems(List<InfoItem> allItems) {
        if (allItems == null) return new ArrayList<>();
        return allItems.stream()
                .filter(item -> item instanceof StreamInfoItem)
                .map(item -> (StreamInfoItem) item)
                .collect(Collectors.toList());
    }

    /**
     * Your original SearchResult class.
     */
    public static class SearchResult {
        private final List<StreamInfoItem> streamItems;
        private final List<InfoItem> allItems;
        private final int pageNumber;
        private final boolean hasMorePages;
        private final int totalResultsSoFar;

        public SearchResult(List<StreamInfoItem> streamItems, List<InfoItem> allItems,
                            int pageNumber, boolean hasMorePages, int totalResultsSoFar) {
            this.streamItems = streamItems;
            this.allItems = allItems;
            this.pageNumber = pageNumber;
            this.hasMorePages = hasMorePages;
            this.totalResultsSoFar = totalResultsSoFar;
        }

        public List<StreamInfoItem> getStreamItems() { return streamItems; }
        public List<InfoItem> getAllItems() { return allItems; }
        public int getPageNumber() { return pageNumber; }
        public boolean hasMorePages() { return hasMorePages; }
        public int getTotalResultsSoFar() { return totalResultsSoFar; }
    }
}