package it.gov.pagopa.fdr.conversion.service;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.fdr.conversion.util.ErrorTableColumns;
import it.gov.pagopa.fdr.conversion.util.ErrorEnum;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class DeadLetter {
    private static final String tableStorageConnString = System.getenv("TABLE_STORAGE_CONN_STRING");
    private static final String tableName = System.getenv("ERROR_TABLE_NAME");
    private static TableServiceClient tableServiceClient;

    private static TableServiceClient getTableServiceClient(){
        if(tableServiceClient == null){
            tableServiceClient = new TableServiceClientBuilder()
                    .connectionString(tableStorageConnString)
                    .buildClient();
            tableServiceClient.createTableIfNotExists(tableName);
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
        errorMap.put(ErrorTableColumns.columnFieldBlobMetadata, metadata);
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
            TableClient tableClient = getTableServiceClient().getTableClient(tableName);
            TableEntity entity = new TableEntity(pKey, rowKey);
            entity.setProperties(values);
            tableClient.createEntity(entity);
        } catch (Exception e) {
            ctx.getLogger().severe(String.format("[Exception][id=%s] Dead-letter write failed, class = %s, message = %s", ctx.getInvocationId(), e.getClass(), e.getMessage()));
        }
    }
}
