package com.kios.mock.pos.config;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AzureStorageConfig {

    /**
     * When {@code azure.storage.connection-string} is set (local / dev), use it directly.
     * Otherwise fall back to DefaultAzureCredential (managed identity / env vars / etc.).
     */

    @Bean
    @ConditionalOnProperty(name = "azure.storage.connection-string")
    public BlobServiceClient blobServiceClientFromConnectionString(
            @Value("${azure.storage.connection-string}") String connectionString) {
        return new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
    }

    @Bean
    @ConditionalOnProperty(name = "azure.storage.connection-string", matchIfMissing = true)
    public BlobServiceClient blobServiceClientFromIdentity(
            @Value("${azure.storage.account-url}") String accountUrl) {
        return new BlobServiceClientBuilder()
                .endpoint(accountUrl)
                .credential(new DefaultAzureCredentialBuilder().build())
                .buildClient();
    }
}
