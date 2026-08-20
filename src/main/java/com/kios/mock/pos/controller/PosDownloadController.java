package com.kios.mock.pos.controller;

import com.kios.mock.pos.service.BlobDownloadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import java.io.IOException;

/**
 * REST controller that exposes a single endpoint for downloading a JSON blob
 * from Azure Blob Storage as a zipped file.
 *
 * <pre>
 * GET /api/pos/download?blobName=folder/my-data.json
 * </pre>
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/pos")
@RequiredArgsConstructor
@Tag(name = "POS Download", description = "Download JSON blobs from Azure Storage as ZIP archives")
public class PosDownloadController {

    private final BlobDownloadService blobDownloadService;

    /**
     * Downloads a JSON blob from Azure Blob Storage, compresses it into a ZIP
     * archive, and returns it as an {@code application/zip} attachment.
     *
     * @param blobName relative path of the blob inside the configured container,
     *                 e.g. {@code pos/transactions/2024-01-15.json}
     * @return ZIP file as a downloadable attachment
     */
    @Operation(
            summary = "Download blob as ZIP",
            description = "Fetches the specified JSON blob from Azure Blob Storage, wraps it in a ZIP archive, and streams it as a file download.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "ZIP file returned successfully"),
                    @ApiResponse(responseCode = "400", description = "blobName is blank or blob does not exist"),
                    @ApiResponse(responseCode = "500", description = "Unexpected error while reading or compressing the blob")
            }
    )
    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadAsZip(
            @Parameter(description = "Blob name / path inside the container, e.g. pos/data.json", required = true)
            @RequestParam @NotBlank String blobName) {

        log.info("Received download request for blob '{}'", blobName);

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

        // Derive a friendly archive filename from the blob path
        String baseName = blobName.contains("/")
                ? blobName.substring(blobName.lastIndexOf('/') + 1)
                : blobName;
        // Strip .json if present, then add .zip
        String zipFileName = baseName.replaceAll("(?i)\\.json$", "") + ".zip";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipFileName + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(zipBytes.length)
                .body(zipBytes);
    }
}
