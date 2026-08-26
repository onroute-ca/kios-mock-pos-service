package com.kios.mock.pos.controller;

import com.kios.mock.pos.service.BlobDownloadService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MockMenuServiceController.class)
class PosDownloadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BlobDownloadService blobDownloadService;

    /** Reset the in-memory override before every test. */
    @BeforeEach
    void resetOverride() throws Exception {
        mockMvc.perform(patch("/API/v1/Menu/blob-path")
                .contentType("text/plain")
                .content(""));
    }

    // ─── POST /API/v1/Menu ────────────────────────────────────────────────────

    @Test
    void checkMenu_returnsZipAttachment() throws Exception {
        byte[] zipBytes = buildSampleZip("STORE1_v2.json", "{\"menu\":\"data\"}");
        // default blob path: mock-catalogsync/STORE1_v2.json  (blobFolder resolved from @Value)
        when(blobDownloadService.downloadAsZip("mock-catalogsync/STORE1_v2.json")).thenReturn(zipBytes);

        mockMvc.perform(post("/API/v1/Menu")
                        .header("x-api-key", "test-key")
                        .header("x-from", "test-client")
                        .header("x-franchise", "FR001")
                        .header("x-RequestType", "CHECK_MENU")
                        .header("x-StoreNumber", "STORE1")
                        .header("x-MenuVersion", "v2"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"menu-v2.zip\""))
                .andExpect(content().contentType("application/zip"));
    }

    @Test
    void checkMenu_blobNotFound_returns400() throws Exception {
        when(blobDownloadService.downloadAsZip("mock-catalogsync/STORE2_v1.json"))
                .thenThrow(new IllegalArgumentException("Blob not found"));

        mockMvc.perform(post("/API/v1/Menu")
                        .header("x-api-key", "test-key")
                        .header("x-from", "test-client")
                        .header("x-franchise", "FR001")
                        .header("x-RequestType", "CHECK_MENU")
                        .header("x-StoreNumber", "STORE2")
                        .header("x-MenuVersion", "v1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkMenu_missingRequiredHeader_returns400() throws Exception {
        mockMvc.perform(post("/API/v1/Menu")
                        // deliberately omitting x-StoreNumber
                        .header("x-api-key", "test-key")
                        .header("x-from", "test-client")
                        .header("x-franchise", "FR001")
                        .header("x-RequestType", "CHECK_MENU")
                        .header("x-MenuVersion", "v1"))
                .andExpect(status().isBadRequest());
    }

    // ─── PATCH /API/v1/Menu/blob-path ─────────────────────────────────────────

    @Test
    void setBlobPath_setsOverride() throws Exception {
        mockMvc.perform(patch("/API/v1/Menu/blob-path")
                        .contentType("text/plain")
                        .content("custom/path/menu.json"))
                .andExpect(status().isOk())
                .andExpect(content().string("Blob path set to: custom/path/menu.json"));
    }

    @Test
    void setBlobPath_emptyBody_resetsToDefault() throws Exception {
        mockMvc.perform(patch("/API/v1/Menu/blob-path")
                        .contentType("text/plain")
                        .content(""))
                .andExpect(status().isOk())
                .andExpect(content().string("Blob path reset to default (derived from headers)"));
    }

    // ─── GET /API/v1/Menu/blob-path ───────────────────────────────────────────

    @Test
    void getBlobPath_default_returnsDefaultMessage() throws Exception {
        mockMvc.perform(get("/API/v1/Menu/blob-path"))
                .andExpect(status().isOk())
                .andExpect(content().string("default (derived from x-StoreNumber and x-MenuVersion headers)"));
    }

    @Test
    void getBlobPath_afterOverride_returnsOverride() throws Exception {
        mockMvc.perform(patch("/API/v1/Menu/blob-path")
                .contentType("text/plain")
                .content("override/menu.json"));

        mockMvc.perform(get("/API/v1/Menu/blob-path"))
                .andExpect(status().isOk())
                .andExpect(content().string("override/menu.json"));
    }

    // ─── GET /API/v1/Menu/sas-url ─────────────────────────────────────────────

    @Test
    void getSasUrl_returnsSasUrl() throws Exception {
        when(blobDownloadService.generateSasUrl("mock-catalogsync/STORE3_v3.json"))
                .thenReturn("https://example.blob.core.windows.net/container/blob?sig=token");

        mockMvc.perform(get("/API/v1/Menu/sas-url")
                        .header("x-api-key", "test-key")
                        .header("x-StoreNumber", "STORE3")
                        .header("x-MenuVersion", "v3"))
                .andExpect(status().isOk())
                .andExpect(content().string("https://example.blob.core.windows.net/container/blob?sig=token"));
    }

    @Test
    void getSasUrl_blobNotFound_returns400() throws Exception {
        when(blobDownloadService.generateSasUrl("mock-catalogsync/STORE4_v4.json"))
                .thenThrow(new IllegalArgumentException("Blob not found"));

        mockMvc.perform(get("/API/v1/Menu/sas-url")
                        .header("x-api-key", "test-key")
                        .header("x-StoreNumber", "STORE4")
                        .header("x-MenuVersion", "v4"))
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
