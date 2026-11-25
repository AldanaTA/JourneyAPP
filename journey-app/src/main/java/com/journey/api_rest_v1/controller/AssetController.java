package com.journey.api_rest_v1.controller;

import java.time.Duration;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.journey.api_rest_v1.service.AssetService;
import com.journey.api_rest_v1.service.AssetService.AssetFile;

/**
 * Serves stored images under {@code /api/v1/assets/{id}}. Rule bodies reference images as
 * {@code ![alt](asset:<id>)}; the frontend turns that into this URL.
 */
@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    // An asset's file never changes: re-importing a different image creates a new asset id.
    private static final CacheControl IMMUTABLE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private final AssetService assetService;

    /** Wires the asset service. */
    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    /** Streams the image file with its media type. */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> getAsset(@PathVariable String id) {
        AssetFile file = assetService.getAssetFile(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .contentLength(file.byteSize())
                .cacheControl(IMMUTABLE)
                .header("X-Content-Type-Options", "nosniff")
                .body(file.resource());
    }
}
