package com.nidoham.newpipe.stream;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SearchSuggestionsExtractor {
    
    private final StreamingService service;
    private final SuggestionExtractor extractor;
    private final ExecutorService executorService;
    
    public SearchSuggestionsExtractor() {
        this.service = ServiceList.YouTube;
        this.extractor = service.getSuggestionExtractor();
        this.executorService = Executors.newSingleThreadExecutor();
    }
    
    public void getSuggestions(String query, SearchSuggestionsCallback callback) {
        if (query == null || query.trim().isEmpty()) {
            callback.onError(new IllegalArgumentException("Query cannot be empty"));
            return;
        }
        
        executorService.execute(() -> {
            try {
                List<String> suggestions = extractor.suggestionList(query.trim());
                callback.onSuggestionsLoaded(suggestions);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }
    
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
    
    public boolean isShutdown() {
        return executorService == null || executorService.isShutdown();
    }
}