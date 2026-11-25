package com.journey.api_rest_v1.service;

import org.springframework.core.io.Resource;

/**
 * Serves stored images (assets) from the asset storage directory shared with the rules importer.
 */
public interface AssetService {

    /** A stored file ready to stream: its contents, media type, and size in bytes. */
    record AssetFile(Resource resource, String contentType, long byteSize) {
    }

    /** Returns the file for an asset id; 404 when the asset or its file does not exist. */
    AssetFile getAssetFile(String id);
}
