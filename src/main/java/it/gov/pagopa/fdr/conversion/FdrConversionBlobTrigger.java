package it.gov.pagopa.fdr.conversion;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.*;
import feign.Feign;
import feign.FeignException;
import it.gov.pagopa.fdr.conversion.client.FdR1Client;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Slf4j
public class FdrConversionBlobTrigger {
    private static final String fn = "FdR3-to-FdR1";
    private static final Integer MAX_RETRY_COUNT = 10;
    @Setter
    private String fdrFase1BaseUrl = System.getenv("FDR_FASE1_BASE_URL");
    private final String fdrFase1ApiKey = System.getenv("FDR_FASE1_API_KEY");

    /**
     * The job of this function is to convert, i.e., store and save, the streams of FdR3 to FdR1.
     * This is done by calling the API provided by FdR1
     * @param content
     * @param blobName
     * @param blobMetadata
     * @param context
     */
    @FunctionName("ConversionFdr3Blob")
    @ExponentialBackoffRetry(maxRetryCount = 10, maximumInterval = "01:30:00", minimumInterval = "00:00:45")
    public void process (
            @BlobTrigger(
                    name = "Fdr3BlobTrigger",
                    dataType = "binary",
                    path = "%BLOB_STORAGE_FDR3_CONTAINER%/{blobName}",
                    connection = "FDR_SA_CONNECTION_STRING")
            byte[] content,
            @BindingName("blobName") String blobName,
            @BindingName("Metadata") Map<String, String> blobMetadata,
            final ExecutionContext context) {

        int retryIndex = context.getRetryContext() == null ? -1 : context.getRetryContext().getRetrycount();
        Logger logger = context.getLogger();
        logger.info(String.format("[%s] Triggered, blob-name = %s, blob-metadata = %s, retry = %s", fn, blobName, blobMetadata, retryIndex));

        if (retryIndex == MAX_RETRY_COUNT) {
            logger.log(Level.WARNING, () -> String.format("[ALERT][%s][LAST_RETRY] Performing last retry for event ingestion: InvocationId [%s]",
                    fn, context.getInvocationId()));
            // save to dead-letter table here todo
        }

        FdR1Client fdR1Client = Feign.builder()
                .target(FdR1Client.class, fdrFase1BaseUrl);

        // Retry is configured at function level, we don't catch all Exception to trigger that retry
        try {
            fdR1Client.postGzipFile(fdrFase1ApiKey, content);
        } catch (FeignException e) {
            logger.severe(String.format("[%s][Exception] %s, response-status = %s, message = %s", fn, e.getClass(), e.status(), e.getMessage()));
            throw e;
        }
    }
}
