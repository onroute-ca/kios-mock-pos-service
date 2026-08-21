package com.kios.mock.pos.controller;

import com.kios.mock.pos.service.BlobDownloadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mocks the external POS CHECK_MENU API.
 * Default blob path: {azure.storage.blob-folder}/{storeNumber}_{menuVersion}.json
 * Override via PATCH /API/v1/Menu/blob-path
 */
@Slf4j
@RestController
@RequestMapping("/API")
@RequiredArgsConstructor
@Tag(name = "POS Mock Menu", description = "Mocks the external POS check_menu API")
public class MockMenuServiceController {

        private final BlobDownloadService blobDownloadService;

        @Value("${azure.storage.blob-folder}")
        private String blobFolder;

        /** Holds an optional override blob path set via PATCH. Null means use the default derived from headers. */
        private final AtomicReference<String> overrideBlobPath = new AtomicReference<>(null);

        // ─── PATCH: configure blob path ───────────────────────────────────────

        @Operation(
                summary = "Override blob path",
                description = "Sets a custom blob path that the POST /v1/Menu endpoint will use instead of deriving it from the request headers. Send an empty body to reset to the default.",
                responses = {
                                @ApiResponse(responseCode = "200", description = "Blob path updated")
                }
        )
        @PatchMapping("/v1/Menu/blob-path")
        public ResponseEntity<String> setBlobPath(@RequestBody(required = false) String blobPath) {
                String trimmed = (blobPath != null) ? blobPath.trim().replaceAll("^\"|\"$", "") : "";
                if (trimmed.isEmpty()) {
                        overrideBlobPath.set(null);
                        log.info("Blob path override cleared — will derive from request headers");
                        return ResponseEntity.ok("Blob path reset to default (derived from headers)");
                }
                overrideBlobPath.set(trimmed);
                log.info("Blob path override set to '{}'", trimmed);
                return ResponseEntity.ok("Blob path set to: " + trimmed);
        }

        @Operation(summary = "Mock POS CHECK_MENU", description = "Reads menu JSON from Azure Blob Storage and streams it as a ZIP download.", responses = {
                        @ApiResponse(responseCode = "200", description = "ZIP file returned successfully"),
                        @ApiResponse(responseCode = "400", description = "A required header is missing or the blob does not exist"),
                        @ApiResponse(responseCode = "500", description = "Unexpected error while reading or compressing the blob")
        })
        @PostMapping("/v1/Menu")
        public ResponseEntity<byte[]> checkMenu(
                        @RequestHeader("x-api-key") String apiKey,
                        @RequestHeader("x-from") String from,
                        @RequestHeader("x-franchise") String franchiseId,
                        @RequestHeader("x-RequestType") String requestType,
                        @RequestHeader("x-StoreNumber") String storeNumber,
                        @RequestHeader("x-MenuVersion") String menuVersion) {

                // Use the override blob path if set via PATCH, otherwise derive from headers
                String override = overrideBlobPath.get();
                String blobName = (override != null)
                                ? override
                                : "%s/%s_%s.json".formatted(blobFolder, storeNumber, menuVersion);
                log.info("Mock CHECK_MENU - store={}, franchise={}, version={}, blob='{}' ({})",
                                storeNumber, franchiseId, menuVersion, blobName,
                                override != null ? "override" : "derived");

                byte[] zipBytes;
                try {
                        zipBytes = blobDownloadService.downloadAsZip(blobName);
                } catch (IllegalArgumentException ex) {
                        log.warn("Blob not found: {}", ex.getMessage());
                        return ResponseEntity.badRequest().build();
                } catch (IOException ex) {
                        log.error("Failed to download/compress blob '{}': {}", blobName, ex.getMessage(), ex);
                        return ResponseEntity.internalServerError().build();
                }

                String zipFileName = "menu-%s.zip".formatted(menuVersion);
                return ResponseEntity.ok()
                                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipFileName + "\"")
                                .contentType(MediaType.parseMediaType("application/zip"))
                                .contentLength(zipBytes.length)
                                .body(zipBytes);
        }
}
