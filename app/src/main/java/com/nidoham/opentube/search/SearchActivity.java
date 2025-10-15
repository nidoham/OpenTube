package com.nidoham.opentube.search;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;

import com.nidoham.flowtube.player.playqueue.PlayQueue;
import com.nidoham.flowtube.player.playqueue.PlayQueueItem;
import com.nidoham.flowtube.player.playqueue.SimplePlayQueue;

import com.nidoham.newpipe.stream.SearchCallback;
import com.nidoham.newpipe.stream.SearchQueryExtractor;
import com.nidoham.newpipe.stream.SearchSuggestionsCallback;
import com.nidoham.newpipe.stream.SearchSuggestionsExtractor;
import com.nidoham.opentube.databinding.ActivitySearchBinding;
import com.nidoham.opentube.player.PlayerActivity;
import java.util.Arrays;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchActivity extends AppCompatActivity implements SearchCallback, SearchSuggestionsCallback {

    private static final String PREF_SEARCH_HISTORY = "search_history";
    private static final String KEY_SEARCH_HISTORY = "history_items";
    private static final int MAX_HISTORY_ITEMS = 10;

    private ActivitySearchBinding binding;
    private SearchSuggestionAdapter suggestionAdapter;
    private SearchHistoryAdapter historyAdapter;
    private SearchResultAdapter resultAdapter;
    
    private List<String> searchSuggestions = new ArrayList<>();
    private List<String> searchHistory = new ArrayList<>();
    private List<StreamInfoItem> searchResults = new ArrayList<>();
    
    private SearchQueryExtractor searchExtractor;
    private SearchSuggestionsExtractor suggestionsExtractor;
    private String currentQuery = "";
    private boolean isLoadingMore = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initializeExtractors();
        initializeViews();
        setupAdapters();
        setupListeners();
        loadSearchHistory();
        showSuggestionsPanel();
    }

    private void initializeExtractors() {
        searchExtractor = new SearchQueryExtractor(ServiceList.YouTube, "");
        searchExtractor.setCallback(this);
        suggestionsExtractor = new SearchSuggestionsExtractor();
    }

    private void initializeViews() {
        binding.searchEditText.requestFocus();
        // Removed: getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        // This line attempts to force the soft keyboard visible, which can cause issues or crashes on Android TV
        // where a software keyboard might not be present by default or preferred.
        // The system will typically handle keyboard visibility when an EditText is focused.
    }

    private void setupAdapters() {
        suggestionAdapter = new SearchSuggestionAdapter(searchSuggestions, this::onSuggestionClicked);
        binding.suggestionsList.setAdapter(suggestionAdapter);
        binding.suggestionsList.setLayoutManager(new LinearLayoutManager(this));

        historyAdapter = new SearchHistoryAdapter(searchHistory, this::onHistoryItemClicked);
        historyAdapter.setOnSearchHistoryLongClickListener(this::onHistoryItemLongClicked);

        resultAdapter = new SearchResultAdapter(searchResults, this::onSearchResultClicked);
        binding.itemsList.setLayoutManager(new LinearLayoutManager(this));
        binding.itemsList.setAdapter(resultAdapter);
        
        // Add scroll listener for pagination
        binding.itemsList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null && !isLoadingMore && searchExtractor.hasMoreResults()) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                    
                    if ((firstVisibleItem + visibleItemCount) >= totalItemCount - 5) {
                        loadMoreResults();
                    }
                }
            }
        });
    }

    private void setupListeners() {
        binding.backButton.setOnClickListener(v -> onBackPressed());
        binding.searchButton.setOnClickListener(v -> performSearch());
        binding.clearRecentButton.setOnClickListener(v -> clearSearchHistory());

        binding.searchEditText.setOnEditorActionListener(this::onEditorAction);
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                onSearchTextChanged(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.suggestionsPanel.setOnClickListener(v -> {
            android.view.inputmethod.InputMethodManager imm = 
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            // Add null checks for InputMethodManager and window token for robustness on Android TV
            if (imm != null && binding.suggestionsPanel.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(binding.suggestionsPanel.getWindowToken(), 0);
            }
        });
    }

    private boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            performSearch();
            return true;
        }
        return false;
    }

    private void onSearchTextChanged(String query) {
        if (query.trim().isEmpty()) {
            showSuggestionsPanel();
        } else {
            fetchSearchSuggestions(query.trim());
        }
    }

    private void performSearch() {
        String query = binding.searchEditText.getText().toString().trim();
        if (query.isEmpty()) {
            return;
        }

        hideKeyboard();
        addToSearchHistory(query);
        showLoadingState();
        executeSearch(query);
    }

    private void executeSearch(String query) {
        currentQuery = query;
        searchResults.clear();
        resultAdapter.setSearchResults(searchResults);
        
        // Reset and create new extractor for the new query
        searchExtractor.reset();
        searchExtractor = new SearchQueryExtractor(ServiceList.YouTube, query);
        searchExtractor.setCallback(this);
        searchExtractor.performInitialSearch();
    }

    private void loadMoreResults() {
        if (!isLoadingMore && searchExtractor.hasMoreResults()) {
            isLoadingMore = true;
            searchExtractor.fetchNextPage();
        }
    }

    private void onSuggestionClicked(String suggestion) {
        binding.searchEditText.setText(suggestion);
        binding.searchEditText.setSelection(suggestion.length());
        performSearch();
    }

    private void onHistoryItemClicked(String historyItem) {
        binding.searchEditText.setText(historyItem);
        binding.searchEditText.setSelection(historyItem.length());
        performSearch();
    }

    private boolean onHistoryItemLongClicked(String historyItem, int position) {
        removeFromSearchHistory(position);
        return true;
    }

    private void onSearchResultClicked(StreamInfoItem streamInfo) {
        try {
            // 1. Create the single item
            PlayQueueItem item = PlayQueueItem.from(streamInfo);

            // 2. Create a List containing that single item
            List<PlayQueueItem> itemList = new ArrayList<>();
            itemList.add(item);
            
            // 3. Create the queue with the list (SimplePlayQueue is serializable)
            SimplePlayQueue queue = new SimplePlayQueue(false, 0, itemList);

            // 4. Put it in the Intent
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("queue", queue);
            startActivity(intent);
            
        } catch (Exception e) {
            // Handle any serialization or other errors
            Snackbar.make(binding.getRoot(), 
                    "Unable to play video: " + e.getMessage(), 
                    Snackbar.LENGTH_LONG)
                    .show();
            e.printStackTrace();
        }
    }

    private void showSuggestionsPanel() {
        binding.suggestionsPanel.setVisibility(android.view.View.VISIBLE);
        binding.itemsList.setVisibility(android.view.View.GONE);
        binding.loadingProgressBar.setVisibility(android.view.View.GONE);
        binding.emptyStateView.setVisibility(android.view.View.GONE);
        binding.correctionCard.setVisibility(android.view.View.GONE);

        if (searchHistory.isEmpty()) {
            binding.recentHeaderLayout.setVisibility(android.view.View.GONE);
        } else {
            binding.recentHeaderLayout.setVisibility(android.view.View.VISIBLE);
        }

        binding.suggestionsList.setAdapter(historyAdapter);
    }

    private void showSearchResults() {
        binding.suggestionsPanel.setVisibility(android.view.View.GONE);
        binding.itemsList.setVisibility(android.view.View.VISIBLE);
        binding.loadingProgressBar.setVisibility(android.view.View.GONE);
        
        if (searchResults.isEmpty()) {
            binding.emptyStateView.setVisibility(android.view.View.VISIBLE);
        } else {
            binding.emptyStateView.setVisibility(android.view.View.GONE);
        }
    }

    private void showLoadingState() {
        binding.suggestionsPanel.setVisibility(android.view.View.GONE);
        binding.itemsList.setVisibility(android.view.View.GONE);
        binding.emptyStateView.setVisibility(android.view.View.GONE);
        binding.loadingProgressBar.setVisibility(android.view.View.VISIBLE);
        binding.correctionCard.setVisibility(android.view.View.GONE);
    }

    private void fetchSearchSuggestions(String query) {
        binding.suggestionsList.setAdapter(suggestionAdapter);
        binding.recentHeaderLayout.setVisibility(android.view.View.GONE);

        if (!suggestionsExtractor.isShutdown()) {
            suggestionsExtractor.getSuggestions(query, this);
        }
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm = 
            (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        // Add null checks for InputMethodManager and window token for robustness on Android TV
        if (imm != null && binding.searchEditText.getWindowToken() != null) {
            imm.hideSoftInputFromWindow(binding.searchEditText.getWindowToken(), 0);
        }
    }

    private void loadSearchHistory() {
        SharedPreferences prefs = getSharedPreferences(PREF_SEARCH_HISTORY, Context.MODE_PRIVATE);
        Set<String> historySet = prefs.getStringSet(KEY_SEARCH_HISTORY, new HashSet<>());
        
        searchHistory.clear();
        searchHistory.addAll(historySet);
        historyAdapter.setSearchHistory(searchHistory);
    }

    private void saveSearchHistory() {
        SharedPreferences prefs = getSharedPreferences(PREF_SEARCH_HISTORY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        Set<String> historySet = new HashSet<>(searchHistory);
        editor.putStringSet(KEY_SEARCH_HISTORY, historySet);
        editor.apply();
    }

    private void addToSearchHistory(String query) {
        searchHistory.remove(query); // Remove if already exists
        searchHistory.add(0, query); // Add to beginning
        
        // Limit history size
        if (searchHistory.size() > MAX_HISTORY_ITEMS) {
            searchHistory = new ArrayList<>(searchHistory.subList(0, MAX_HISTORY_ITEMS));
        }
        
        saveSearchHistory();
        historyAdapter.setSearchHistory(searchHistory);
    }

    private void removeFromSearchHistory(int position) {
        if (position >= 0 && position < searchHistory.size()) {
            searchHistory.remove(position);
            historyAdapter.setSearchHistory(searchHistory);
            saveSearchHistory();
            
            if (searchHistory.isEmpty()) {
                binding.recentHeaderLayout.setVisibility(android.view.View.GONE);
            }
        }
    }

    private void clearSearchHistory() {
        searchHistory.clear();
        historyAdapter.setSearchHistory(searchHistory);
        binding.recentHeaderLayout.setVisibility(android.view.View.GONE);
        saveSearchHistory();
    }

    // SearchCallback implementation
    @Override
    public void onSearchStarted(String query) {
        runOnUiThread(() -> {
            if (!isLoadingMore) {
                showLoadingState();
            }
        });
    }

    @Override
    public void onSearchCompleted(SearchQueryExtractor.SearchResult result) {
        runOnUiThread(() -> {
            isLoadingMore = false;
            searchResults.clear();
            searchResults.addAll(result.getStreamItems());
            resultAdapter.setSearchResults(searchResults);
            showSearchResults();
        });
    }

    @Override
    public void onPageLoadStarted(int pageNumber) {
        runOnUiThread(() -> {
            isLoadingMore = true;
            // You can show a loading indicator at the bottom of the list here if desired
        });
    }

    @Override
    public void onPageLoadCompleted(SearchQueryExtractor.SearchResult result) {
        runOnUiThread(() -> {
            isLoadingMore = false;
            int previousSize = searchResults.size();
            searchResults.addAll(result.getStreamItems());
            resultAdapter.notifyItemRangeInserted(previousSize, result.getStreamItems().size());
        });
    }

    @Override
    public void onSearchError(Exception error) {
        runOnUiThread(() -> {
            isLoadingMore = false;
            binding.loadingProgressBar.setVisibility(android.view.View.GONE);
            
            String errorMessage = "Search failed: " + error.getMessage();
            Snackbar.make(binding.getRoot(), errorMessage, Snackbar.LENGTH_LONG)
                    .setAction("Retry", v -> {
                        if (!currentQuery.isEmpty()) {
                            executeSearch(currentQuery);
                        }
                    })
                    .show();
            
            if (searchResults.isEmpty()) {
                binding.emptyStateView.setVisibility(android.view.View.VISIBLE);
            }
        });
    }

    @Override
    public void onSearchCancelled() {
        runOnUiThread(() -> {
            isLoadingMore = false;
            binding.loadingProgressBar.setVisibility(android.view.View.GONE);
        });
    }

    // SearchSuggestionsCallback implementation
    @Override
    public void onSuggestionsLoaded(List<String> suggestions) {
        runOnUiThread(() -> {
            searchSuggestions.clear();
            searchSuggestions.addAll(suggestions);
            suggestionAdapter.setSuggestions(searchSuggestions);
        });
    }

    @Override
    public void onError(Exception error) {
        // Handle suggestions error silently or show a subtle indication
        runOnUiThread(() -> {
            searchSuggestions.clear();
            suggestionAdapter.setSuggestions(searchSuggestions);
        });
    }
    
    @Deprecated
    @Override
    public void onBackPressed() {
        if (binding.suggestionsPanel.getVisibility() == android.view.View.VISIBLE) {
            super.onBackPressed();
        } else {
            binding.searchEditText.setText("");
            showSuggestionsPanel();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (suggestionsExtractor != null) {
            suggestionsExtractor.shutdown();
        }
        if (searchExtractor != null) {
            searchExtractor.reset();
        }
        binding = null;
    }
}