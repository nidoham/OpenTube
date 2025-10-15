package com.nidoham.opentube.search;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.nidoham.opentube.databinding.ItemSearchSuggestionBinding;
import java.util.List;

public class SearchHistoryAdapter extends RecyclerView.Adapter<SearchHistoryAdapter.SearchHistoryViewHolder> {

    private List<String> searchHistory;
    private OnSearchHistoryClickListener listener;
    private OnSearchHistoryLongClickListener longClickListener;

    public SearchHistoryAdapter(List<String> searchHistory) {
        this.searchHistory = searchHistory;
    }

    public SearchHistoryAdapter(List<String> searchHistory, OnSearchHistoryClickListener listener) {
        this.searchHistory = searchHistory;
        this.listener = listener;
    }

    public void setSearchHistory(List<String> newSearchHistory) {
        this.searchHistory = newSearchHistory;
        notifyDataSetChanged();
    }

    public void setOnSearchHistoryClickListener(OnSearchHistoryClickListener listener) {
        this.listener = listener;
    }

    public void setOnSearchHistoryLongClickListener(OnSearchHistoryLongClickListener longClickListener) {
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public SearchHistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemSearchSuggestionBinding binding = ItemSearchSuggestionBinding.inflate(inflater, parent, false);
        return new SearchHistoryViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchHistoryViewHolder holder, int position) {
        String searchItem = searchHistory.get(position);
        holder.bind(searchItem);
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null && position != RecyclerView.NO_POSITION) {
                listener.onSearchHistoryClick(searchItem);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null && position != RecyclerView.NO_POSITION) {
                return longClickListener.onSearchHistoryLongClick(searchItem, position);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return searchHistory != null ? searchHistory.size() : 0;
    }

    public static class SearchHistoryViewHolder extends RecyclerView.ViewHolder {
        private final ItemSearchSuggestionBinding binding;

        public SearchHistoryViewHolder(@NonNull ItemSearchSuggestionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(String searchItem) {
            binding.suggestionText.setText(searchItem);
            // You can customize the icon for history items if needed
            // binding.searchIcon.setImageResource(R.drawable.ic_history);
        }
    }

    public interface OnSearchHistoryClickListener {
        void onSearchHistoryClick(String searchItem);
    }

    public interface OnSearchHistoryLongClickListener {
        boolean onSearchHistoryLongClick(String searchItem, int position);
    }
}