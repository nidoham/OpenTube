package com.nidoham.opentube.search;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.nidoham.newpipe.image.ThumbnailExtractor;
import com.nidoham.opentube.databinding.ItemSearchResultBinding;
import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import java.util.List;
import com.nidoham.opentube.R;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.SearchResultViewHolder> {

    private List<StreamInfoItem> searchResults;
    private OnSearchResultClickListener listener;

    public SearchResultAdapter(List<StreamInfoItem> searchResults) {
        this.searchResults = searchResults;
    }

    public SearchResultAdapter(List<StreamInfoItem> searchResults, OnSearchResultClickListener listener) {
        this.searchResults = searchResults;
        this.listener = listener;
    }

    public void setSearchResults(List<StreamInfoItem> newSearchResults) {
        this.searchResults = newSearchResults;
        notifyDataSetChanged();
    }

    public void setOnSearchResultClickListener(OnSearchResultClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public SearchResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemSearchResultBinding binding = ItemSearchResultBinding.inflate(inflater, parent, false);
        return new SearchResultViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchResultViewHolder holder, int position) {
        StreamInfoItem streamInfoItem = searchResults.get(position);
        holder.bind(streamInfoItem);
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null && position != RecyclerView.NO_POSITION) {
                listener.onSearchResultClick(streamInfoItem);
            }
        });
    }

    @Override
    public int getItemCount() {
        return searchResults != null ? searchResults.size() : 0;
    }

    public static class SearchResultViewHolder extends RecyclerView.ViewHolder {
        private final ItemSearchResultBinding binding;

        public SearchResultViewHolder(@NonNull ItemSearchResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(StreamInfoItem streamInfoItem) {
            // Set video title
            binding.videoTitle.setText(streamInfoItem.getName());
            
            // Set uploader name
            binding.videoUploader.setText(streamInfoItem.getUploaderName());
            
            // Set duration if available
            long duration = streamInfoItem.getDuration();
            if (duration > 0) {
                String durationText = formatDuration(duration);
                // If you have a duration TextView in your layout, uncomment below:
                // binding.videoDuration.setText(durationText);
            }
            
            // Set view count if available
            long viewCount = streamInfoItem.getViewCount();
            if (viewCount >= 0) {
                String viewCountText = formatViewCount(viewCount);
                // If you have a view count TextView in your layout, uncomment below:
                // binding.videoViews.setText(viewCountText);
            }
            
            loadThumbnail(streamInfoItem.getThumbnails());
        }

        private void loadThumbnail(List<Image> thubnails) {
            ThumbnailExtractor image = new ThumbnailExtractor(thubnails);
            String thumbnailUrl = image.getThumbnail();
            
            Glide.with(binding.videoThumbnail.getContext())
                 .load(thumbnailUrl)
                 .placeholder(R.drawable.placeholder_image)
                 .error(R.drawable.placeholder_image)
                 .centerCrop()
                 .into(binding.videoThumbnail);
        }

        private String formatDuration(long durationSeconds) {
            if (durationSeconds < 0) return "";
            
            long hours = durationSeconds / 3600;
            long minutes = (durationSeconds % 3600) / 60;
            long seconds = durationSeconds % 60;
            
            if (hours > 0) {
                return String.format("%d:%02d:%02d", hours, minutes, seconds);
            } else {
                return String.format("%d:%02d", minutes, seconds);
            }
        }

        private String formatViewCount(long viewCount) {
            if (viewCount < 0) return "";
            
            if (viewCount < 1000) {
                return viewCount + " views";
            } else if (viewCount < 1000000) {
                return String.format("%.1fK views", viewCount / 1000.0);
            } else if (viewCount < 1000000000) {
                return String.format("%.1fM views", viewCount / 1000000.0);
            } else {
                return String.format("%.1fB views", viewCount / 1000000000.0);
            }
        }
    }

    public interface OnSearchResultClickListener {
        void onSearchResultClick(StreamInfoItem streamInfoItem);
    }
}