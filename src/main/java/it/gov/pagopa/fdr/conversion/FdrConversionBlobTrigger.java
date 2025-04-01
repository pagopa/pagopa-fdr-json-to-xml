package it.gov.pagopa.fdr.conversion;

import com.google.gson.JsonObject;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.*;
import feign.Feign;
import feign.FeignException;
import it.gov.pagopa.fdr.conversion.client.FdR1Client;
import it.gov.pagopa.fdr.conversion.exception.AlertAppException;
import it.gov.pagopa.fdr.conversion.util.StorageAccountUtil;
import it.gov.pagopa.fdr.conversion.model.ErrorEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.Base64;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static it.gov.pagopa.fdr.conversion.exception.AlertAppException.getExceptionDetails;

@Slf4j
public class FdrConversionBlobTrigger {
    private static final String fn = "FdR3-to-FdR1";
    private static final Integer MAX_RETRY_COUNT = 4;
    private static final String ELABORATE_KEY = "elaborate";
    public String FDR_FASE1_BASE_URL = System.getenv("FDR_FASE1_BASE_URL");
    private final String FDR_FASE1_API_KEY = System.getenv("FDR_FASE1_API_KEY");
    private static final String SESSION_ID_METADATA_KEY = "sessionId";

    /**
     * The job of this function is to convert, i.e., store and save, the streams of FdR3 to FdR1.
     * This is done by calling the API provided by FdR1
     * @param content
     * @param blobName
     * @param blobMetadata
     * @param context
     *
     * Retry mechanism focus
     * ExponentialBackoffRetry will perform N = 5 backoff retries multiplied by N = 1 retries
     * via poisonBlobThreshold mechanism specified in the host.json,
     * in total 5 retries and 1 write attempts on dead-letter that will overwrite the same record in the table.
     * Specifically these will be the retry index values = 0, 1, 2, 3, 4
     */
    @FunctionName("BlobEventProcessor")
    @ExponentialBackoffRetry(maxRetryCount = 4, maximumInterval = "00:05:00", minimumInterval = "00:00:10")
    public boolean process (
            @BlobTrigger(
                    name = "Fdr3BlobTrigger",
                    dataType = "binary",
                    path = "%BLOB_STORAGE_FDR3_CONTAINER%/{blobName}",
                    connection = "FDR_SA_CONNECTION_STRING")
            byte[] content,
            @BindingName("blobName") String blobName,
            @BindingName("Metadata") Map<String, String> blobMetadata,
            final ExecutionContext context) {
        Logger logger = context.getLogger() == null ? Logger.getLogger("BlobEventLogger") : context.getLogger();
        int retryIndex = context.getRetryContext() == null ? -1 : context.getRetryContext().getRetrycount();
        String iid = context.getInvocationId();
        logger.info(String.format("[%s] Triggered, id = %s, blob-name = %s, blob-metadata = %s, retry = %s", fn, iid, blobName, blobMetadata, retryIndex));

        // Ignore the blob if it does not contain the elaborate key or if it isn't true
        if (!Boolean.parseBoolean(blobMetadata.getOrDefault(ELABORATE_KEY, "false"))) {
            logger.info(String.format("[%s] Skipped, id = %s, blob-name = %s, blob-metadata = %s, retry = %s", fn, iid, blobName, blobMetadata, retryIndex));
            return false;
        }

        // Retry is configured at function level, we always make the exception throw to trigger that retry
        try {
            FdR1Client fdR1Client = Feign.builder().target(FdR1Client.class, FDR_FASE1_BASE_URL);
            fdR1Client.postConversion(FDR_FASE1_API_KEY, getPayload(content));
            logger.info(String.format("[%s][id=%s] Successful conversion call to FdR1", fn, context.getInvocationId()));
        }
        catch (Exception e) {
            logger.severe(String.format("[Exception][%s][id=%s] class = %s, message = %s", fn, iid, e.getClass(), e.getMessage()));
            // Get error type
            ErrorEnum errorType = (e instanceof FeignException) ? ErrorEnum.HTTP_ERROR : ErrorEnum.GENERIC_ERROR;
            // Last retry check
            if (retryIndex >= MAX_RETRY_COUNT) {
                sendToDeadLetter(context, blobName, blobMetadata, e.getMessage(), errorType, e.getMessage(), e);
                String exceptionDetails = getExceptionDetails(blobName, blobMetadata.get(SESSION_ID_METADATA_KEY), retryIndex);
                throw new AlertAppException(e.getMessage(), e.getCause(), exceptionDetails);
            }
            throw e;
        }
        return true;
    }

    // Create JSON object with the base64 encoded payload
    public static String getPayload(byte[] content) {
        String base64Content = Base64.getEncoder().encodeToString(content);
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("payload", base64Content);
        jsonBody.addProperty("encoding", "base64");
        return jsonBody.toString();
    }

    // Check retry index and save to dead-letter if max-retry has been reached
    private static void sendToDeadLetter(ExecutionContext context, String blob, Map<String, String> metadata, String message, ErrorEnum errorEnum, String response, Exception e) {
        context.getLogger().log(Level.WARNING, () -> String.format("[ALERT][%s][LAST_RETRY][DEAD-LETTER] Performed last retry for event ingestion: InvocationId [%s]",
                fn, context.getInvocationId()));
        StorageAccountUtil.sendToErrorTable(context, blob, metadata, message, errorEnum, response, e);
    }
}
