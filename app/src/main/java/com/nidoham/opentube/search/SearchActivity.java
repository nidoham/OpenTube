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
import com.nidoham.opentube.databinding.ActivitySearchBinding;
import com.nidoham.opentube.player.PlayerActivity;
// --- Change 1: Import the new RxJava-based services ---
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

// --- Change 2: Remove the old callback interfaces ---
public class SearchActivity extends AppCompatActivity {

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

    // --- Change 3: Hold instances of the services and a CompositeDisposable for cleanup ---
    private SearchService searchService;
    private SearchSuggestionService suggestionService;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private String currentQuery = "";
    private boolean isLoadingMore = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        initializeServices();
        initializeViews();
        setupAdapters();
        setupListeners();
        loadSearchHistory();
        showSuggestionsPanel();
    }

    private void initializeServices() {
        // We will create the SearchService only when a query is actually made.
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

                // --- Change 4: Check hasMoreResults on the service instance ---
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

        // Create a new service for the new query. This correctly resets the state.
        searchService = new SearchService(ServiceList.YouTube, query);

        Disposable searchDisposable = searchService.performInitialSearch()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        // onSuccess: Replaces onSearchCompleted
                        result -> {
                            isLoadingMore = false;
                            searchResults.clear();
                            searchResults.addAll(result.getStreamItems());
                            resultAdapter.setSearchResults(searchResults);
                            showSearchResults();
                        },
                        // onError: Replaces onSearchError
                        this::handleSearchError
                );

        disposables.add(searchDisposable);
    }

    private void loadMoreResults() {
        if (isLoadingMore || searchService == null) return;

        isLoadingMore = true;
        // Optionally show a loading footer in the adapter here

        Disposable nextPageDisposable = searchService.fetchNextPage()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        // onSuccess: Replaces onPageLoadCompleted
                        result -> {
                            isLoadingMore = false;
                            int previousSize = searchResults.size();
                            searchResults.addAll(result.getStreamItems());
                            resultAdapter.notifyItemRangeInserted(previousSize, result.getStreamItems().size());
                            // Optionally hide loading footer here
                        },
                        // onError: Replaces onSearchError for pagination
                        error -> {
                            isLoadingMore = false;
                            handleSearchError(error);
                            // Optionally hide loading footer here
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
                        // onSuccess: Replaces onSuggestionsLoaded
                        suggestions -> {
                            searchSuggestions.clear();
                            searchSuggestions.addAll(suggestions);
                            suggestionAdapter.setSuggestions(searchSuggestions);
                        },
                        // onError: Replaces onError for suggestions
                        error -> {
                            searchSuggestions.clear();
                            suggestionAdapter.setSuggestions(searchSuggestions);
                        }
                );
        disposables.add(suggestionDisposable);
    }

    private void handleSearchError(Throwable error) {
        isLoadingMore = false;
        showSearchResults(); // This will show the empty state if results are empty

        String errorMessage = "Search failed: " + error.getMessage();
        Snackbar.make(binding.getRoot(), errorMessage, Snackbar.LENGTH_LONG)
                .setAction("Retry", v -> {
                    if (!currentQuery.isEmpty()) {
                        executeSearch(currentQuery);
                    }
                })
                .show();
    }


    // --- UNCHANGED METHODS BELOW (No logic changes needed) ---

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
            // Create a PlayQueueItem from your stream information
            PlayQueueItem item = PlayQueueItem.from(streamInfo);

            // Create a list to hold the item(s) for the queue
            List<PlayQueueItem> itemList = new ArrayList<>();
            itemList.add(item);

            // Use the new PlayQueue class instead of SimplePlayQueue
            // The 'complete' flag is now the 'repeatEnabled' flag at the end of the constructor.
            PlayQueue queue = new PlayQueue(0, itemList, false);

            // Create the intent and pass the new PlayQueue object
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
        searchHistory.remove(query);
        searchHistory.add(0, query);
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
                binding.recentHeaderLayout.setVisibility(View.GONE);
            }
        }
    }

    private void clearSearchHistory() {
        searchHistory.clear();
        historyAdapter.setSearchHistory(searchHistory);
        binding.recentHeaderLayout.setVisibility(View.GONE);
        saveSearchHistory();
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
        disposables.clear();
        binding = null;
    }
}