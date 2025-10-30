package it.gov.pagopa.fdr.conversion;

import static it.gov.pagopa.fdr.conversion.exception.AlertAppException.getExceptionDetails;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.*;
import feign.Feign;
import feign.FeignException;
import it.gov.pagopa.fdr.conversion.client.AppInsightTelemetryClient;
import it.gov.pagopa.fdr.conversion.client.FdR1Client;
import it.gov.pagopa.fdr.conversion.model.ErrorEnum;
import it.gov.pagopa.fdr.conversion.util.StorageAccountUtil;

import java.io.IOException;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FdrConversionBlobTrigger {
  private static final String FN_NAME = "FdR3-to-FdR1";
  private static final Integer MAX_RETRY_COUNT = 4;
  private static final String ELABORATE_KEY = "elaborate";
  private static final String SESSION_ID_METADATA_KEY = "sessionId";

  private final String fdrFase1ApiKey = System.getenv("FDR_FASE1_API_KEY");

  private static FdR1Client fdR1Client;
  private static AppInsightTelemetryClient aiTelemetryClient;

  static {
    if (aiTelemetryClient == null) {
      aiTelemetryClient = new AppInsightTelemetryClient();
    }
    if (fdR1Client == null) {
      fdR1Client = Feign.builder().target(FdR1Client.class, System.getenv("FDR_FASE1_BASE_URL"));
    }
  }

  static void setClientsForTest(FdR1Client testFdR1Client, AppInsightTelemetryClient testAiTelemetryClient) {
    fdR1Client = testFdR1Client;
    aiTelemetryClient = testAiTelemetryClient;
  }

  public FdrConversionBlobTrigger() {}


  /**
   * The job of this function is to convert, i.e., store and save, the streams of FdR3 to FdR1. This
   * is done by calling the API provided by FdR1
   *
   * @param content FDR3 flow from blob storage
   * @param blobName FDR3 flow blob name
   * @param blobMetadata FDR3 flow blob metadata
   * @param context
   *     <p>Retry mechanism focus ExponentialBackoffRetry will perform N = 5 backoff retries
   *     multiplied by N = 1 retries via poisonBlobThreshold mechanism specified in the host.json,
   *     in total 5 retries and 1 write attempts on dead-letter that will overwrite the same record
   *     in the table. Specifically these will be the retry index values = 0, 1, 2, 3, 4
   */
  @FunctionName("BlobEventProcessor")
  @ExponentialBackoffRetry(
          maxRetryCount = 4,
          maximumInterval = "00:05:00",
          minimumInterval = "00:00:10")
  public boolean process(
          @BlobTrigger(
                  name = "Fdr3BlobTrigger",
                  dataType = "binary",
                  path = "%BLOB_STORAGE_FDR3_CONTAINER%/{blobName}",
                  connection = "FDR_SA_CONNECTION_STRING")
          byte[] content,
          @BindingName("blobName") String blobName,
          @BindingName("Metadata") Map<String, String> blobMetadata,
          final ExecutionContext context) throws IOException {

    int retryIndex =
            context.getRetryContext() == null ? -1 : context.getRetryContext().getRetrycount();
    String iid = context.getInvocationId();

    log.info(
            "[{}] Triggered, id = {}, blob-name = {}, blob-metadata = {}, retry = {}",
            FN_NAME,
            iid,
            blobName,
            blobMetadata,
            retryIndex);

    // Ignore the blob if it does not contain the elaborate key or if it isn't true
    if (!Boolean.parseBoolean(blobMetadata.getOrDefault(ELABORATE_KEY, "false"))) {
      log.info(
              "[{}] Skipped, id = {}, blob-name = {}, blob-metadata = {}, retry = {}",
              FN_NAME,
              iid,
              blobName,
              blobMetadata,
              retryIndex);
      return false;
    }

    // Retry is configured at function level, we always make the exception throw to trigger that retry
    try {
        fdR1Client.postConversion(fdrFase1ApiKey, content);
        log.info(
                "[{}][id={}] Successful conversion call to FdR1",
                FN_NAME,
                context.getInvocationId()
        );

    } catch (Exception | Error e) {
      log.error(
              "[Exception][{}][id={}] class = {}, message = {}",
              FN_NAME,
              iid,
              e.getClass(),
              e.getMessage());
      // Get error type
      ErrorEnum errorType =
              (e instanceof FeignException) ? ErrorEnum.HTTP_ERROR : ErrorEnum.GENERIC_ERROR;
      // Last retry check
      if (retryIndex >= MAX_RETRY_COUNT) {
        sendToDeadLetter(context, blobName, blobMetadata, e.getMessage(), errorType, e.getMessage(), e);
        String exceptionDetails =
                getExceptionDetails(blobName, blobMetadata.get(SESSION_ID_METADATA_KEY), retryIndex);

        aiTelemetryClient.createCustomEventForAlert(exceptionDetails, e);
      }
      throw e;
    }
    return true;
  }

  // Check retry index and save to dead-letter if max-retry has been reached
  private static void sendToDeadLetter(
      ExecutionContext context,
      String blob,
      Map<String, String> metadata,
      String message,
      ErrorEnum errorEnum,
      String response,
      Object error) {
    log.warn(
        "[ALERT][{}][LAST_RETRY][DEAD-LETTER] Performed last retry for event ingestion: InvocationId [{}]",
        FN_NAME,
        context.getInvocationId());
    StorageAccountUtil.sendToErrorTable(context, blob, metadata, message, errorEnum, response, error);
  }
}
