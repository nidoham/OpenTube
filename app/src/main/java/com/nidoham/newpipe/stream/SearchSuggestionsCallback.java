package com.nidoham.newpipe.stream;

import java.util.List;

public interface SearchSuggestionsCallback {
    void onSuggestionsLoaded(List<String> suggestions);
    void onError(Exception error);
}