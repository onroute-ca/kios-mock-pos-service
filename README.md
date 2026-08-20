# kios-mock-pos-service

A lightweight Spring Boot service that exposes a single REST endpoint for downloading JSON blobs from **Azure Blob Storage** as compressed ZIP archives.

## Endpoint

```
GET /api/pos/download?blobName=<blob-path>
```

| Parameter  | Description                                               | Example                      |
|------------|-----------------------------------------------------------|------------------------------|
| `blobName` | Relative path of the blob inside the configured container | `pos/transactions/2024-01.json` |

**Response:** `application/zip` attachment – filename derived from the blob name (`.json` → `.zip`).

## Configuration

| Property | Env var | Default | Description |
|---|---|---|---|
| `azure.storage.connection-string` | `AZURE_STORAGE_CONNECTION_STRING` | _(optional)_ | Use for local/dev with Azurite |
| `azure.storage.account-url` | `AZURE_STORAGE_ACCOUNT_URL` | — | Storage account endpoint (Managed Identity) |
| `azure.storage.container-name` | `AZURE_STORAGE_CONTAINER_NAME` | `pos-data` | Blob container name |

## Running locally

```bash
# Using Azurite (local Azure Storage emulator):
export AZURE_STORAGE_CONNECTION_STRING="UseDevelopmentStorage=true"
export AZURE_STORAGE_CONTAINER_NAME="pos-data"
make run
```

Swagger UI available at: <http://localhost:8085/swagger-ui.html>

## Build & Docker

```bash
make build          # compile & package
make docker-build   # build Docker image
```
