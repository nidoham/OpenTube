package com.nidoham.stream.data;

import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;

import java.util.List;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class SearchSuggestionService {

    private final SuggestionExtractor extractor;
    private final StreamingService service;

    public SearchSuggestionService() {
        this.service = ServiceList.YouTube;
        this.extractor = service.getSuggestionExtractor();
    }

    public Single<List<String>> getSuggestions(final String query) {
        return Single.fromCallable(() -> {
                    if (query == null || query.trim().isEmpty()) {
                        throw new IllegalArgumentException("Query cannot be empty");
                    }
                    return extractor.suggestionList(query.trim());
                })
                .subscribeOn(Schedulers.io());
    }
}