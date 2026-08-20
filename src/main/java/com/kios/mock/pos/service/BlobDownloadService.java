package com.kios.mock.pos.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
