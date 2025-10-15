package com.nidoham.newpipe.stream;

/**
 * Callback interface for search operations
 */
public interface SearchCallback {
    
    /**
     * Called when a search operation starts
     * @param query The search query being executed
     */
    void onSearchStarted(String query);
    
    /**
     * Called when a search operation completes successfully
     * @param result The search results
     */
    void onSearchCompleted(SearchQueryExtractor.SearchResult result);
    
    /**
     * Called when a page load operation starts
     * @param pageNumber The page number being loaded
     */
    void onPageLoadStarted(int pageNumber);
    
    /**
     * Called when a page load operation completes successfully
     * @param result The page results
     */
    void onPageLoadCompleted(SearchQueryExtractor.SearchResult result);
    
    /**
     * Called when an error occurs during search or page loading
     * @param error The exception that occurred
     */
    void onSearchError(Exception error);
    
    /**
     * Called when search is cancelled
     */
    void onSearchCancelled();
}