package com.nidoham.opentube.search;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.nidoham.opentube.databinding.ItemSearchSuggestionBinding;
import java.util.List;

public class SearchSuggestionAdapter extends RecyclerView.Adapter<SearchSuggestionAdapter.SuggestionViewHolder> {

    private List<String> suggestions;
    private OnSuggestionClickListener listener;

    public SearchSuggestionAdapter(List<String> suggestions) {
        this.suggestions = suggestions;
    }

    public SearchSuggestionAdapter(List<String> suggestions, OnSuggestionClickListener listener) {
        this.suggestions = suggestions;
        this.listener = listener;
    }

    public void setSuggestions(List<String> newSuggestions) {
        this.suggestions = newSuggestions;
        notifyDataSetChanged();
    }

    public void setOnSuggestionClickListener(OnSuggestionClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public SuggestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemSearchSuggestionBinding binding = ItemSearchSuggestionBinding.inflate(inflater, parent, false);
        return new SuggestionViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SuggestionViewHolder holder, int position) {
        String suggestion = suggestions.get(position);
        holder.bind(suggestion);
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null && position != RecyclerView.NO_POSITION) {
                listener.onSuggestionClick(suggestion);
            }
        });
    }

    @Override
    public int getItemCount() {
        return suggestions != null ? suggestions.size() : 0;
    }

    public static class SuggestionViewHolder extends RecyclerView.ViewHolder {
        private final ItemSearchSuggestionBinding binding;

        public SuggestionViewHolder(@NonNull ItemSearchSuggestionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(String suggestion) {
            binding.suggestionText.setText(suggestion);
        }
    }

    public interface OnSuggestionClickListener {
        void onSuggestionClick(String suggestion);
    }
}