package it.gov.pagopa.fdr.conversion.service;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.fdr.conversion.BlobData;
import it.gov.pagopa.fdr.conversion.util.ErrorTableColumns;
import it.gov.pagopa.fdr.conversion.util.ErrorEnum;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class StorageAccountUtil {
    private static final String STORAGE_CONNECTION_STRING = System.getenv("TABLE_STORAGE_CONN_STRING");
    private static final String ERROR_TABLE_NAME = System.getenv("ERROR_TABLE_NAME");
    private static final String FDR3_FLOW_BLOB_CONTAINER_NAME = System.getenv("BLOB_STORAGE_FDR3_CONTAINER");
    private static TableServiceClient tableServiceClient;
    private static BlobContainerClient blobContainerClient;

    private static TableServiceClient getTableServiceClient(){
        if(tableServiceClient == null){
            tableServiceClient = new TableServiceClientBuilder()
                    .connectionString(STORAGE_CONNECTION_STRING)
                    .buildClient();
            tableServiceClient.createTableIfNotExists(ERROR_TABLE_NAME);
        }
        return tableServiceClient;
    }

    public static void sendToErrorTable(ExecutionContext ctx, String blob, Map<String, String> metadata, String message, ErrorEnum errorEnum, String httpErrorResponse, Exception e){
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Map<String,Object> errorMap = new LinkedHashMap<>();
        // The id could also be the blob name, since it is unique, however in case we have an error from dead-letter 2 times
        // for the same blob name following manual retries, we could not track the second error event
        errorMap.put(ErrorTableColumns.columnFieldId, id);
        errorMap.put(ErrorTableColumns.columnFieldBlob, blob);
        errorMap.put(ErrorTableColumns.columnFieldSessionId, metadata.getOrDefault("sessionId", "not-found"));
        errorMap.put(ErrorTableColumns.columnFieldCreated, now);
        errorMap.put(ErrorTableColumns.columnFieldMessage, message);
        errorMap.put(ErrorTableColumns.columnFieldErrorType, errorEnum.name());
        errorMap.put(ErrorTableColumns.columnFieldHttpErrorResposne, httpErrorResponse);
        errorMap.put(ErrorTableColumns.columnFieldStackTrace, ExceptionUtils.getStackTrace(e));

        String partitionKey = now.toString().substring(0,10);

        createTableEntity(ctx, partitionKey, id, errorMap);
    }

    public static void createTableEntity(ExecutionContext ctx, String pKey, String rowKey, Map<String,Object> values) {
        try {
            TableClient tableClient = getTableServiceClient().getTableClient(ERROR_TABLE_NAME);
            TableEntity entity = new TableEntity(pKey, rowKey);
            entity.setProperties(values);
            tableClient.createEntity(entity);
        } catch (Exception e) {
            ctx.getLogger().severe(String.format("[Exception][id=%s] Dead-letter write failed, class = %s, message = %s", ctx.getInvocationId(), e.getClass(), e.getMessage()));
        }
    }

    public static BlobData getBlobContent(String fileName) {
        BlobContainerClient blobContainer = getBlobContainerClient();
        BlobClient blobClient = blobContainer.getBlobClient(fileName);
        Map<String, String> metadata = new HashMap<>(blobClient.getProperties().getMetadata());
        return BlobData.builder()
                .fileName(fileName)
                .metadata(metadata)
                .content(blobClient.downloadContent().toBytes())
                .build();
    }

    private static BlobContainerClient getBlobContainerClient() {
        if (blobContainerClient == null) {
            blobContainerClient = new BlobServiceClientBuilder()
                    .connectionString(STORAGE_CONNECTION_STRING)
                    .buildClient()
                    .getBlobContainerClient(FDR3_FLOW_BLOB_CONTAINER_NAME);
        }
        return blobContainerClient;
    }
}
