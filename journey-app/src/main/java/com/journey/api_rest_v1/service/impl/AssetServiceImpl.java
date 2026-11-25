package com.journey.api_rest_v1.service.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.journey.api_rest_v1.domain.rules.Asset;
import com.journey.api_rest_v1.repository.AssetRepository;
import com.journey.api_rest_v1.service.AssetService;

/**
 * Default {@link AssetService}: looks the asset up by id and streams its file from
 * {@code journey.assets.dir} (the same volume the rules importer writes to).
 *
 * <p>The stored path comes from the database, but it is still resolved and checked to stay inside the storage
 * directory, so a bad row can never expose other files on the server.</p>
 */
@Service
public class AssetServiceImpl implements AssetService {

    private final AssetRepository assetRepository;
    private final Path storageRoot;

    /** Wires the repository and the storage directory. */
    public AssetServiceImpl(AssetRepository assetRepository, @Value("${journey.assets.dir}") String storageDir) {
        this.assetRepository = assetRepository;
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
    }

    @Override
    @Transactional(readOnly = true)
    public AssetFile getAssetFile(String id) {
        Asset asset = assetRepository.findById(parseUuid(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found: " + id));

        Path file = storageRoot.resolve(asset.getFilePath()).normalize();
        if (!file.startsWith(storageRoot) || !Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset file is missing: " + id);
        }
        return new AssetFile(new FileSystemResource(file), asset.getContentType(), asset.getByteSize());
    }

    private static UUID parseUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid asset id: " + id);
        }
    }
}
