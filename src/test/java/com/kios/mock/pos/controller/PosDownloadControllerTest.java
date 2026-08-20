package com.kios.mock.pos.controller;

import com.kios.mock.pos.service.BlobDownloadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PosDownloadController.class)
class PosDownloadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BlobDownloadService blobDownloadService;

    @Test
    void downloadAsZip_returnsZipAttachment() throws Exception {
        String blobName = "pos/transactions.json";
        byte[] zipBytes = buildSampleZip("transactions.json", "{\"key\":\"value\"}");

        when(blobDownloadService.downloadAsZip(blobName)).thenReturn(zipBytes);

        mockMvc.perform(get("/api/pos/download").param("blobName", blobName))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"transactions.zip\""))
                .andExpect(content().contentType("application/zip"));
    }

    @Test
    void downloadAsZip_blobNotFound_returns400() throws Exception {
        String blobName = "pos/missing.json";

        when(blobDownloadService.downloadAsZip(blobName))
                .thenThrow(new IllegalArgumentException("Blob not found"));

        mockMvc.perform(get("/api/pos/download").param("blobName", blobName))
                .andExpect(status().isBadRequest());
    }

    @Test
    void downloadAsZip_blankBlobName_returns400() throws Exception {
        mockMvc.perform(get("/api/pos/download").param("blobName", "   "))
                .andExpect(status().isBadRequest());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private byte[] buildSampleZip(String entryName, String content) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
