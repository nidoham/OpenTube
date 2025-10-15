package com.nidoham.flowtube.player.streams;

import org.schabi.newpipe.extractor.stream.StreamInfo;

/**
 * Callback interface for handling stream info extraction results.
 */
public interface StreamInfoCallback {
    void onLoading();
    void onSuccess(StreamInfo streamInfo);
    void onError(Exception error);
}