package com.nidoham.opentube.search;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.nidoham.opentube.databinding.ActivitySearchBinding;
import com.nidoham.opentube.player.PlayerActivity;
import com.nidoham.opentube.search.SearchHistoryManager;
import com.nidoham.stream.data.SearchService;
import com.nidoham.stream.data.SearchSuggestionService;

import com.nidoham.stream.player.playqueue.PlayQueue;
import com.nidoham.stream.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public class SearchActivity extends AppCompatActivity {

    private static final int MAX_HISTORY_ITEMS = 10;

    private ActivitySearchBinding binding;
    private SearchSuggestionAdapter suggestionAdapter;
    private SearchHistoryAdapter historyAdapter;
    private SearchResultAdapter resultAdapter;

    private List<String> searchSuggestions = new ArrayList<>();
    private List<String> searchHistory = new ArrayList<>();
    private List<SearchHistoryManager.SearchQuery> searchQueryObjects = new ArrayList<>();
    private List<StreamInfoItem> searchResults = new ArrayList<>();

    private SearchService searchService;
    private SearchSuggestionService suggestionService;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private SearchHistoryManager searchHistoryManager;

    private String currentQuery = "";
    private boolean isLoadingMore = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initializeFirebase();
        initializeServices();
        initializeViews();
        setupAdapters();
        setupListeners();
        loadSearchHistory();
        showSuggestionsPanel();
    }

    private void initializeFirebase() {
        // TODO: Replace with your actual user ID from Firebase Auth
        // Example: FirebaseAuth.getInstance().getCurrentUser().getUid()
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid().toString(); // Replace this with actual user ID
        
        searchHistoryManager = new SearchHistoryManager(userId);
        
        // Attach real-time listener for automatic updates
        searchHistoryManager.attachRealtimeListener(new SearchHistoryManager.OnHistoryFetchListener() {
            @Override
            public void onSuccess(List<SearchHistoryManager.SearchQuery> queries) {
                // Update the UI with new search history
                searchQueryObjects.clear();
                searchQueryObjects.addAll(queries);
                
                searchHistory.clear();
                for (SearchHistoryManager.SearchQuery query : queries) {
                    searchHistory.add(query.getQueryText());
                }
                
                historyAdapter.setSearchHistory(searchHistory);
                
                // Update visibility of recent header
                if (binding.suggestionsPanel.getVisibility() == View.VISIBLE) {
                    if (searchHistory.isEmpty()) {
                        binding.recentHeaderLayout.setVisibility(View.GONE);
                    } else {
                        binding.recentHeaderLayout.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onFailure(String error) {
                // Silently handle errors for real-time updates
                // You can log this if needed
            }
        });
    }

    private void initializeServices() {
        suggestionService = new SearchSuggestionService();
    }

    private void initializeViews() {
        binding.searchEditText.requestFocus();
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

        binding.itemsList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();

                if (layoutManager != null && !isLoadingMore && searchService != null && searchService.hasMoreResults()) {
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

    private void executeSearch(String query) {
        currentQuery = query;
        showLoadingState();

        searchService = new SearchService(ServiceList.YouTube, query);

        Disposable searchDisposable = searchService.performInitialSearch()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        result -> {
                            isLoadingMore = false;
                            searchResults.clear();
                            searchResults.addAll(result.getStreamItems());
                            resultAdapter.setSearchResults(searchResults);
                            showSearchResults();
                        },
                        this::handleSearchError
                );

        disposables.add(searchDisposable);
    }

    private void loadMoreResults() {
        if (isLoadingMore || searchService == null) return;

        isLoadingMore = true;

        Disposable nextPageDisposable = searchService.fetchNextPage()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        result -> {
                            isLoadingMore = false;
                            int previousSize = searchResults.size();
                            searchResults.addAll(result.getStreamItems());
                            resultAdapter.notifyItemRangeInserted(previousSize, result.getStreamItems().size());
                        },
                        error -> {
                            isLoadingMore = false;
                            handleSearchError(error);
                        }
                );
        disposables.add(nextPageDisposable);
    }

    private void fetchSearchSuggestions(String query) {
        binding.suggestionsList.setAdapter(suggestionAdapter);
        binding.recentHeaderLayout.setVisibility(View.GONE);

        Disposable suggestionDisposable = suggestionService.getSuggestions(query)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        suggestions -> {
                            searchSuggestions.clear();
                            searchSuggestions.addAll(suggestions);
                            suggestionAdapter.setSuggestions(searchSuggestions);
                        },
                        error -> {
                            searchSuggestions.clear();
                            suggestionAdapter.setSuggestions(searchSuggestions);
                        }
                );
        disposables.add(suggestionDisposable);
    }

    private void handleSearchError(Throwable error) {
        isLoadingMore = false;
        showSearchResults();

        String errorMessage = "Search failed: " + error.getMessage();
        Snackbar.make(binding.getRoot(), errorMessage, Snackbar.LENGTH_LONG)
                .setAction("Retry", v -> {
                    if (!currentQuery.isEmpty()) {
                        executeSearch(currentQuery);
                    }
                })
                .show();
    }

    private void setupListeners() {
        binding.backButton.setOnClickListener(v -> onBackPressed());
        binding.searchButton.setOnClickListener(v -> performSearch());
        binding.clearRecentButton.setOnClickListener(v -> clearSearchHistory());
        binding.searchEditText.setOnEditorActionListener(this::onEditorAction);
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { onSearchTextChanged(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
        binding.suggestionsPanel.setOnClickListener(v -> {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
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
        executeSearch(query);
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
            PlayQueueItem item = PlayQueueItem.from(streamInfo);
            List<PlayQueueItem> itemList = new ArrayList<>();
            itemList.add(item);
            PlayQueue queue = new PlayQueue(0, itemList, false);

            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("queue", queue);
            startActivity(intent);
        } catch (Exception e) {
            Snackbar.make(binding.getRoot(), "Unable to play video: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void showSuggestionsPanel() {
        binding.suggestionsPanel.setVisibility(View.VISIBLE);
        binding.itemsList.setVisibility(View.GONE);
        binding.loadingProgressBar.setVisibility(View.GONE);
        binding.emptyStateView.setVisibility(View.GONE);
        binding.correctionCard.setVisibility(View.GONE);
        if (searchHistory.isEmpty()) {
            binding.recentHeaderLayout.setVisibility(View.GONE);
        } else {
            binding.recentHeaderLayout.setVisibility(View.VISIBLE);
        }
        binding.suggestionsList.setAdapter(historyAdapter);
    }

    private void showSearchResults() {
        binding.loadingProgressBar.setVisibility(View.GONE);
        binding.suggestionsPanel.setVisibility(View.GONE);
        if (searchResults.isEmpty()) {
            binding.itemsList.setVisibility(View.GONE);
            binding.emptyStateView.setVisibility(View.VISIBLE);
        } else {
            binding.itemsList.setVisibility(View.VISIBLE);
            binding.emptyStateView.setVisibility(View.GONE);
        }
    }

    private void showLoadingState() {
        binding.suggestionsPanel.setVisibility(View.GONE);
        binding.itemsList.setVisibility(View.GONE);
        binding.emptyStateView.setVisibility(View.GONE);
        binding.loadingProgressBar.setVisibility(View.VISIBLE);
        binding.correctionCard.setVisibility(View.GONE);
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && binding.searchEditText.getWindowToken() != null) {
            imm.hideSoftInputFromWindow(binding.searchEditText.getWindowToken(), 0);
        }
    }

    private void loadSearchHistory() {
        searchHistoryManager.fetchSearchHistory(new SearchHistoryManager.OnHistoryFetchListener() {
            @Override
            public void onSuccess(List<SearchHistoryManager.SearchQuery> queries) {
                searchQueryObjects.clear();
                searchQueryObjects.addAll(queries);
                
                searchHistory.clear();
                for (SearchHistoryManager.SearchQuery query : queries) {
                    searchHistory.add(query.getQueryText());
                }
                
                historyAdapter.setSearchHistory(searchHistory);
            }

            @Override
            public void onFailure(String error) {
                // Handle error silently or show a message
                Snackbar.make(binding.getRoot(), "Failed to load search history", Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    private void addToSearchHistory(String query) {
        searchHistoryManager.addSearchQuery(query, new SearchHistoryManager.OnOperationListener() {
            @Override
            public void onSuccess() {
                // Real-time listener will automatically update the UI
            }

            @Override
            public void onFailure(String error) {
                // Handle error silently or show a message if needed
            }
        });
    }

    private void removeFromSearchHistory(int position) {
        if (position >= 0 && position < searchQueryObjects.size()) {
            SearchHistoryManager.SearchQuery queryToRemove = searchQueryObjects.get(position);
            
            searchHistoryManager.removeSearchQuery(queryToRemove.getQueryId(), new SearchHistoryManager.OnOperationListener() {
                @Override
                public void onSuccess() {
                    // Real-time listener will automatically update the UI
                }

                @Override
                public void onFailure(String error) {
                    Snackbar.make(binding.getRoot(), "Failed to remove item", Snackbar.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void clearSearchHistory() {
        searchHistoryManager.clearAllSearchHistory(new SearchHistoryManager.OnOperationListener() {
            @Override
            public void onSuccess() {
                // Real-time listener will automatically update the UI
                binding.recentHeaderLayout.setVisibility(View.GONE);
            }

            @Override
            public void onFailure(String error) {
                Snackbar.make(binding.getRoot(), "Failed to clear history", Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (binding.suggestionsPanel.getVisibility() == View.VISIBLE) {
            super.onBackPressed();
        } else {
            disposables.clear();
            binding.searchEditText.setText("");
            searchResults.clear();
            resultAdapter.notifyDataSetChanged();
            showSuggestionsPanel();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchHistoryManager.detachRealtimeListener();
        disposables.clear();
        binding = null;
    }
}
