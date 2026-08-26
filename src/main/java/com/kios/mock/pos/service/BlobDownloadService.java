package com.kios.mock.pos.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Reads a JSON blob from Azure Blob Storage, wraps it in a ZIP archive, and
 * returns the raw ZIP bytes so the controller can stream them as a download.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlobDownloadService {

    private final BlobServiceClient blobServiceClient;

    @Value("${azure.storage.container-name}")
    private String containerName;

    /** How long (in minutes) the generated SAS token remains valid. */
    @Value("${azure.storage.sas-expiry-minutes:60}")
    private long sasExpiryMinutes;

    /**
     * Generates a short-lived SAS URL for {@code blobName}.
     *
     * @param blobName path / name of the blob inside the container
     * @return fully-qualified HTTPS URL with embedded SAS query string
     * @throws IllegalArgumentException if the blob does not exist
     */
    public String generateSasUrl(String blobName) {
        log.info("Generating SAS URL for blob '{}' in container '{}'", blobName, containerName);

        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        if (!blobClient.exists()) {
            throw new IllegalArgumentException(
                    "Blob not found: container='%s', blob='%s'".formatted(containerName, blobName));
        }

        BlobSasPermission permission = new BlobSasPermission().setReadPermission(true);
        BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(
                OffsetDateTime.now().plusMinutes(sasExpiryMinutes), permission);

        String sasToken = blobClient.generateSas(sasValues);
        String sasUrl = blobClient.getBlobUrl() + "?" + sasToken;
        log.info("SAS URL generated for blob '{}' (expires in {} min)", blobName, sasExpiryMinutes);
        return sasUrl;
    }

    /**
     * Downloads the blob content directly from a pre-built SAS URL.
     *
     * @param sasUrl the fully-qualified SAS URL pointing to the blob
     * @return raw bytes of the blob content
     * @throws IOException          if the HTTP call or stream reading fails
     * @throws IllegalStateException if the server returns a non-200 status
     */
    public byte[] downloadFromSasUrl(String sasUrl) throws IOException {
        URI uri = URI.create(sasUrl);
        String redactedUrl = uri.getScheme() + "://" + uri.getAuthority() + uri.getPath();
        log.info("Downloading blob from SAS URL: {} (SAS query redacted)", redactedUrl);

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP request interrupted while downloading from SAS URL", e);
        }

        if (response.statusCode() != 200) {
            throw new IllegalStateException(
                    "Unexpected HTTP status %d when downloading from SAS URL".formatted(response.statusCode()));
        }

        byte[] content = response.body().readAllBytes();
        log.info("Downloaded {} bytes from SAS URL", content.length);
        return content;
    }

    /**
     * Downloads {@code blobName} from the configured container, compresses it
     * into a ZIP archive (keeping the original file name as the ZIP entry), and
     * returns the ZIP bytes.
     *
     * @param blobName path / name of the blob inside the container (e.g. {@code pos/data.json})
     * @return raw bytes of the generated ZIP file
     * @throws IOException          if streaming fails
     * @throws IllegalArgumentException if the blob does not exist
     */
    public byte[] downloadAsZip(String blobName) throws IOException {
        log.info("Downloading blob '{}' from container '{}'", blobName, containerName);

        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(blobName);

        if (!blobClient.exists()) {
            throw new IllegalArgumentException(
                    "Blob not found: container='%s', blob='%s'".formatted(containerName, blobName));
        }

        // Stream the blob into memory
        ByteArrayOutputStream blobBuffer = new ByteArrayOutputStream();
        blobClient.downloadStream(blobBuffer);
        byte[] jsonBytes = blobBuffer.toByteArray();

        log.debug("Blob '{}' downloaded ({} bytes). Compressing…", blobName, jsonBytes.length);

        // Wrap in ZIP
        ByteArrayOutputStream zipBuffer = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipBuffer)) {
            // Use only the last segment as the ZIP entry name
            String entryName = blobName.contains("/")
                    ? blobName.substring(blobName.lastIndexOf('/') + 1)
                    : blobName;

            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(jsonBytes);
            zos.closeEntry();
        }

        byte[] zipBytes = zipBuffer.toByteArray();
        log.info("ZIP created for blob '{}' ({} bytes compressed)", blobName, zipBytes.length);
        return zipBytes;
    }
}
