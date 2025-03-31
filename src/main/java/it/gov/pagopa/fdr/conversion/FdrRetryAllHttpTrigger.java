package it.gov.pagopa.fdr.conversion;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.PagedResponse;
import com.azure.data.tables.models.TableEntity;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import it.gov.pagopa.fdr.conversion.model.BlobData;
import it.gov.pagopa.fdr.conversion.model.ErrorTableColumns;
import it.gov.pagopa.fdr.conversion.util.StorageAccountUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;


@Slf4j
public class FdrRetryAllHttpTrigger {
    private static final String fn = "FdR3-to-FdR1-RetryAll";

    @FunctionName("ErrorRetryFunction")
    public HttpResponseMessage process (
            @HttpTrigger(name = "ConversionFdrHttpTrigger",
                    methods = {HttpMethod.POST},
                    route = "errors/retry",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        Logger logger = context.getLogger() == null ? Logger.getLogger("ErrorRetryFunction") : context.getLogger();
        try {
            PagedIterable<TableEntity> entities = StorageAccountUtil.getTableServiceClient().getTableClient("").listEntities();

            // Iterate through the pages of results
            for (PagedResponse<TableEntity> page : entities.iterableByPage()) {
                // For each page, process the retry
                for (TableEntity e : page.getElements()) {
                    Map<String, Object> properties = e.getProperties();
                    String blobName = (String) properties.get(ErrorTableColumns.COLUMN_FIELD_BLOB);
                    BlobData blobData = StorageAccountUtil.getBlobContent(blobName);
                    FdrConversionBlobTrigger processor = new FdrConversionBlobTrigger();
                    boolean processed = processor.process(blobData.getContent(), blobName, blobData.getMetadata(), context);

                    logger.info(String.format(
                            "[fn=%s][id=%s] Retry table entity processed = %s, PartitionKey = %s, RowKey = %s, Properties = %s, blobName = %s",
                            fn, context.getInvocationId(), processed, e.getPartitionKey(), e.getRowKey(), properties, blobName
                    ));
                }

                String continuationToken = page.getContinuationToken();
                if (continuationToken != null) {
                    logger.info(String.format("[fn=%s][id=%s] More pages available, continue with the token: %s",
                            fn, context.getInvocationId(), continuationToken));
                } else {
                    System.out.println("No more pages available.");
                    logger.info(String.format("[fn=%s][id=%s] No more pages available", fn, context.getInvocationId()));
                }
            }

            return request.createResponseBuilder(HttpStatus.OK).body(HttpStatus.OK.toString()).build();
        } catch (Exception e) {
            logger.severe(String.format("[Exception][id=%s] Error while FDR3 ErrorRetryFunction execution, class = %s, message = %s",
                    context.getInvocationId(), e.getClass(), e.getMessage()));
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body(HttpStatus.INTERNAL_SERVER_ERROR.toString()).build();
        }
    }
}
