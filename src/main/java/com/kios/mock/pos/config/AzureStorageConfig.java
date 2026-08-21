package com.kios.mock.pos.config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureStorageConfig {

    /**
     * Creates the {@link BlobServiceClient}:
     * <ul>
     *   <li>If {@code azure.storage.connection-string} is non-blank → connection string (local/dev)</li>
     *   <li>Otherwise → DefaultAzureCredential against {@code azure.storage.account-url} (staging/prod)</li>
     * </ul>
     */
    @Bean
    public BlobServiceClient blobServiceClient(
            @Value("${azure.storage.connection-string:}") String connectionString,
            @Value("${azure.storage.account-url:}") String accountUrl) {

        if (connectionString != null && !connectionString.isBlank()) {
            return new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();
        }

        if (accountUrl == null || accountUrl.isBlank()) {
            throw new IllegalStateException(
                    "Azure Storage is not configured: set either azure.storage.connection-string or azure.storage.account-url");
        }

        return new BlobServiceClientBuilder()
                .endpoint(accountUrl)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }
}

