package it.gov.pagopa.fdr.conversion;

import com.google.gson.JsonObject;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.*;
import feign.Feign;
import feign.FeignException;
import it.gov.pagopa.fdr.conversion.client.FdR1Client;
import it.gov.pagopa.fdr.conversion.service.DeadLetter;
import it.gov.pagopa.fdr.conversion.util.ErrorEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.Base64;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Slf4j
public class FdrConversionBlobTrigger {
    private static final String fn = "FdR3-to-FdR1";
    private static final Integer MAX_RETRY_COUNT = 10;
    private static final String ELABORATE_KEY = "elaborate";
    public String fdrFase1BaseUrl = System.getenv("FDR_FASE1_BASE_URL");
    private final String fdrFase1ApiKey = System.getenv("FDR_FASE1_API_KEY");

    /**
     * The job of this function is to convert, i.e., store and save, the streams of FdR3 to FdR1.
     * This is done by calling the API provided by FdR1
     * @param content
     * @param blobName
     * @param blobMetadata
     * @param context
     */
    @FunctionName("BlobEventProcessor")
    @ExponentialBackoffRetry(maxRetryCount = 10, maximumInterval = "00:00:30", minimumInterval = "00:00:01")
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

        // Ignore the blob if it does not contain the elaborate key or if it isn't true
        if (!Boolean.parseBoolean(blobMetadata.getOrDefault(ELABORATE_KEY, "false")))
            return;

        // Start ConversionFdr3Blob execution
        Logger logger = context.getLogger();
        int retryIndex = context.getRetryContext() == null ? -1 : context.getRetryContext().getRetrycount();
        logger.info(String.format("[%s] Triggered, blob-name = %s, blob-metadata = %s, retry = %s", fn, blobName, blobMetadata, retryIndex));

        // Retry is configured at function level, we always make the exception throw to trigger that retry
        try {
            FdR1Client fdR1Client = Feign.builder().target(FdR1Client.class, fdrFase1BaseUrl);
            fdR1Client.postConversion(fdrFase1ApiKey, getPayload(content));
            logger.info(String.format("[%s][id=%s] %s, response-status = %s, message = %s",
                    fn, context.getInvocationId()));
        } catch (FeignException e) {
            logger.severe(String.format("[Exception][%s][id=%s] %s, response-status = %s, message = %s",
                    fn, context.getInvocationId(), e.getClass(), e.status(), e.getMessage()));
            deadLetter(context, blobName, blobMetadata, e.getMessage(), ErrorEnum.HTTP_ERROR, e.getMessage(), e);
            throw e;
        } catch (Exception ex) {
            logger.severe(String.format("[Exception][%s][id=%s] Generic Exception class = %s, message = %s",
                    fn, context.getInvocationId(), ex.getClass(), ex.getMessage()));
            deadLetter(context, blobName, blobMetadata, ex.getMessage(), ErrorEnum.GENERIC_ERROR, "generic-error", ex);
            throw ex;
        }
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
    private static void deadLetter(ExecutionContext context, String blob, Map<String, String> metadata, String message, ErrorEnum errorEnum, String response, Exception e) {
        // the retry count ranges from 0 to MAX_RETRY_COUNT-1
        int retryIndex = context.getRetryContext() == null ? -1 : context.getRetryContext().getRetrycount();
        Logger logger = context.getLogger();
        if (retryIndex >= (MAX_RETRY_COUNT-1)) {
            logger.log(Level.WARNING, () -> String.format("[ALERT][%s][LAST_RETRY][DEAD-LETTER] Performed last retry for event ingestion: InvocationId [%s]",
                    fn, context.getInvocationId()));
            DeadLetter.sendToErrorTable(context, blob, metadata, message, errorEnum, response, e);
        }
    }
}
