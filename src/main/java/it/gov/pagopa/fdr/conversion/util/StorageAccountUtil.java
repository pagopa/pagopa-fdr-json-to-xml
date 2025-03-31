package it.gov.pagopa.fdr.conversion.util;

import com.azure.core.http.rest.PagedIterable;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.fdr.conversion.model.BlobData;
import it.gov.pagopa.fdr.conversion.model.ErrorEnum;
import it.gov.pagopa.fdr.conversion.model.ErrorTableColumns;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class StorageAccountUtil {
    private static final String TABLE_CONNECTION_STRING = System.getenv("TABLE_STORAGE_CONN_STRING");
    private static final String BLOB_CONNECTION_STRING = System.getenv("FDR_SA_CONNECTION_STRING");
    private static final String ERROR_TABLE_NAME = System.getenv("ERROR_TABLE_NAME");
    private static final String FDR3_FLOW_BLOB_CONTAINER_NAME = System.getenv("BLOB_STORAGE_FDR3_CONTAINER");
    private static final String SESSION_ID_METADATA_KEY = "sessionId";
    private static final String SESSION_INSERTED_TIMESTAMP_METADATA_KEY = "insertedTimestamp";
    private static TableServiceClient tableServiceClient;
    private static BlobContainerClient blobContainerClient;

    public static TableServiceClient getTableServiceClient(){
        if(tableServiceClient == null){
            tableServiceClient = new TableServiceClientBuilder()
                    .connectionString(TABLE_CONNECTION_STRING)
                    .buildClient();
            tableServiceClient.createTableIfNotExists(ERROR_TABLE_NAME);
        }
        return tableServiceClient;
    }

    private static BlobContainerClient getBlobContainerClient() {
        if (blobContainerClient == null) {
            blobContainerClient = new BlobServiceClientBuilder()
                    .connectionString(BLOB_CONNECTION_STRING)
                    .buildClient()
                    .getBlobContainerClient(FDR3_FLOW_BLOB_CONTAINER_NAME);
        }
        return blobContainerClient;
    }

    public static void sendToErrorTable(ExecutionContext ctx, String blob, Map<String, String> metadata, String message, ErrorEnum errorEnum, String httpErrorResponse, Exception e){
        String defaultSessionId = "NA_"+UUID.randomUUID();
        String sessionId = metadata.getOrDefault(SESSION_ID_METADATA_KEY, defaultSessionId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime insertedTime = LocalDateTime.now();
        try {
            insertedTime = LocalDateTime.parse(metadata.get(SESSION_INSERTED_TIMESTAMP_METADATA_KEY));
        } catch (Exception iEx) {
            ctx.getLogger().severe(String.format("[Exception][id=%s] %s failed conversion the process goes on with now as %s, class = %s, message = %s",
                    ctx.getInvocationId(), SESSION_INSERTED_TIMESTAMP_METADATA_KEY, SESSION_INSERTED_TIMESTAMP_METADATA_KEY, iEx.getClass(), iEx.getMessage()));
        }
        Map<String,Object> errorMap = new LinkedHashMap<>();
        // The id could also be the blob name, since it is unique, however in case we have an error from dead-letter 2 times
        // for the same blob name following manual retries, we could not track the second error event
        errorMap.put(ErrorTableColumns.COLUMN_FIELD_BLOB, blob);
        errorMap.put(ErrorTableColumns.COLUMN_FIELD_SESSION_ID, sessionId);
        errorMap.put(ErrorTableColumns.COLUMN_FIELD_CREATED, now);
        errorMap.put(ErrorTableColumns.COLUMN_FIELD_MESSAGE, message);
        errorMap.put(ErrorTableColumns.COLUMN_FIELD_ERROR_TYPE, errorEnum.name());
        errorMap.put(ErrorTableColumns.COLUMN_FIELD_HTTP_ERROR_RESPOSNE, httpErrorResponse);
        errorMap.put(ErrorTableColumns.COLUMN_FIELD_STACK_TRACE, ExceptionUtils.getStackTrace(e));

        String partitionKey = insertedTime.toString().substring(0,10);

        createTableEntity(ctx, partitionKey, sessionId, errorMap);
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
        if(!blobClient.exists())
            return null;
        Map<String, String> metadata = new HashMap<>(blobClient.getProperties().getMetadata());
        return BlobData.builder()
                .fileName(fileName)
                .metadata(metadata)
                .content(blobClient.downloadContent().toBytes())
                .build();
    }

    public static PagedIterable<TableEntity> getTableEntities() {
        try {
            return getTableServiceClient()
                    .getTableClient(ERROR_TABLE_NAME)
                    .listEntities();
        } catch (Exception e) {
            return null;
        }
    }
}
