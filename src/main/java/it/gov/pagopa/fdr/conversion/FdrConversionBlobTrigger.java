package it.gov.pagopa.fdr.conversion;

import static it.gov.pagopa.fdr.conversion.exception.AlertAppException.getExceptionDetails;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.google.gson.JsonObject;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.*;
import feign.Feign;
import feign.FeignException;
import it.gov.pagopa.fdr.conversion.client.AppInsightTelemetryClient;
import it.gov.pagopa.fdr.conversion.client.FdR1Client;
import it.gov.pagopa.fdr.conversion.exception.AlertAppException;
import it.gov.pagopa.fdr.conversion.model.ErrorEnum;
import it.gov.pagopa.fdr.conversion.util.StorageAccountUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FdrConversionBlobTrigger {
  private static final String FN_NAME = "FdR3-to-FdR1";
  private static final Integer MAX_RETRY_COUNT = 4;
  private static final String ELABORATE_KEY = "elaborate";
  private static final String SESSION_ID_METADATA_KEY = "sessionId";

  public final String fdrSaConnectionString = System.getenv("FDR_SA_CONNECTION_STRING");
  public final String fdrFase1BaseUrl = System.getenv("FDR_FASE1_BASE_URL");
  private final String fdrFase1ApiKey = System.getenv("FDR_FASE1_API_KEY");

  @Getter private final BlobServiceClient blobServiceClient;
  private final FdR1Client fdR1Client;
  private final AppInsightTelemetryClient aiTelemetryClient;

  FdrConversionBlobTrigger(BlobServiceClient blobServiceClient, FdR1Client fdR1Client, AppInsightTelemetryClient aiTelemetryClient) {
    this.blobServiceClient = blobServiceClient;
    this.fdR1Client = fdR1Client;
    this.aiTelemetryClient = aiTelemetryClient;
  }

  public FdrConversionBlobTrigger() {
    this.aiTelemetryClient = new AppInsightTelemetryClient();
    this.fdR1Client = Feign.builder().target(FdR1Client.class, fdrFase1BaseUrl);

    this.blobServiceClient = new BlobServiceClientBuilder()
            .connectionString(this.fdrSaConnectionString)
            .buildClient();
  }

  /**
   * The job of this function is to convert, i.e., store and save, the streams of FdR3 to FdR1. This
   * is done by calling the API provided by FdR1
   *
   * @param dontUse FDR3 flow from blob storage
   * @param blobName FDR3 flow blob name
   * @param blobMetadata FDR3 flow blob metadata
   * @param context
   *     <p>Retry mechanism focus ExponentialBackoffRetry will perform N = 5 backoff retries
   *     multiplied by N = 1 retries via poisonBlobThreshold mechanism specified in the host.json,
   *     in total 5 retries and 1 write attempts on dead-letter that will overwrite the same record
   *     in the table. Specifically these will be the retry index values = 0, 1, 2, 3, 4
   */
  // improvements for memory optimization
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
          byte[] dontUse,
          @BindingName("blobName") String blobName,
          @BindingName("Metadata") Map<String, String> blobMetadata,
          final ExecutionContext context) throws IOException {

    dontUse = null; // help GC

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

      String containerName = System.getenv("BLOB_STORAGE_FDR3_CONTAINER");
      BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
      BlobClient blobClient = containerClient.getBlobClient(blobName);

      // Retry is configured at function level, we always make the exception throw to trigger that retry
      try (InputStream originalInputStream = blobClient.openInputStream()) {
        fdR1Client.postConversion(fdrFase1ApiKey, getPayload(originalInputStream));
        log.info(
                "[{}][id={}] Successful conversion call to FdR1",
                FN_NAME,
                context.getInvocationId()
        );
      }

    } catch (Exception e) {
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
        sendToDeadLetter(
                context, blobName, blobMetadata, e.getMessage(), errorType, e.getMessage(), e);
        String exceptionDetails =
                getExceptionDetails(blobName, blobMetadata.get(SESSION_ID_METADATA_KEY), retryIndex);

        this.aiTelemetryClient.createCustomEventForAlert(exceptionDetails, e);
        throw new AlertAppException(e.getMessage(), e.getCause(), exceptionDetails);
      }
      throw e;
    }
    return true;
  }

//  ORIGINALE
//  @FunctionName("BlobEventProcessor")
//  @ExponentialBackoffRetry(
//      maxRetryCount = 4,
//      maximumInterval = "00:05:00",
//      minimumInterval = "00:00:10")
//  public boolean process(
//      @BlobTrigger(
//              name = "Fdr3BlobTrigger",
//              dataType = "binary",
//              path = "%BLOB_STORAGE_FDR3_CONTAINER%/{blobName}",
//              connection = "FDR_SA_CONNECTION_STRING")
//          byte[] content,
//      @BindingName("blobName") String blobName,
//      @BindingName("Metadata") Map<String, String> blobMetadata,
//      final ExecutionContext context) {
//
//    int retryIndex =
//        context.getRetryContext() == null ? -1 : context.getRetryContext().getRetrycount();
//    String iid = context.getInvocationId();
//
//    log.info(
//        "[{}] Triggered, id = {}, blob-name = {}, blob-metadata = {}, retry = {}",
//        FN_NAME,
//        iid,
//        blobName,
//        blobMetadata,
//        retryIndex);
//
//    // Ignore the blob if it does not contain the elaborate key or if it isn't true
//    if (!Boolean.parseBoolean(blobMetadata.getOrDefault(ELABORATE_KEY, "false"))) {
//      log.info(
//          "[{}] Skipped, id = {}, blob-name = {}, blob-metadata = {}, retry = {}",
//          FN_NAME,
//          iid,
//          blobName,
//          blobMetadata,
//          retryIndex);
//      return false;
//    }
//
//    // Retry is configured at function level, we always make the exception throw to trigger that retry
//    try {
//      fdR1Client.postConversion(fdrFase1ApiKey, getPayload(content));
//      log.info(
//          "[{}][id={}] Successful conversion call to FdR1", FN_NAME, context.getInvocationId());
//    } catch (Exception e) {
//      log.error(
//          "[Exception][{}][id={}] class = {}, message = {}",
//          FN_NAME,
//          iid,
//          e.getClass(),
//          e.getMessage());
//      // Get error type
//      ErrorEnum errorType =
//          (e instanceof FeignException) ? ErrorEnum.HTTP_ERROR : ErrorEnum.GENERIC_ERROR;
//      // Last retry check
//      if (retryIndex >= MAX_RETRY_COUNT) {
//        sendToDeadLetter(
//            context, blobName, blobMetadata, e.getMessage(), errorType, e.getMessage(), e);
//        String exceptionDetails =
//            getExceptionDetails(blobName, blobMetadata.get(SESSION_ID_METADATA_KEY), retryIndex);
//
//        this.aiTelemetryClient.createCustomEventForAlert(exceptionDetails, e);
//        throw new AlertAppException(e.getMessage(), e.getCause(), exceptionDetails);
//      }
//      throw e;
//    }
//    return true;
//  }

  // Create JSON object with the base64 encoded payload
  public static String getPayload(byte[] content) {
    String base64Content = Base64.getEncoder().encodeToString(content);
    JsonObject jsonBody = new JsonObject();
    jsonBody.addProperty("payload", base64Content);
    jsonBody.addProperty("encoding", "base64");
    return jsonBody.toString();
  }

  public static String getPayload(InputStream contentStream) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try (OutputStream base64Output = Base64.getEncoder().wrap(outputStream)) {
      contentStream.transferTo(base64Output);
    }

    // to optimize memory usage, the JSON is created manually
    String base64 = outputStream.toString(StandardCharsets.UTF_8);
    return String.format("{\"payload\":\"%s\",\"encoding\":\"base64\"}", base64);
  }


  // Check retry index and save to dead-letter if max-retry has been reached
  private static void sendToDeadLetter(
      ExecutionContext context,
      String blob,
      Map<String, String> metadata,
      String message,
      ErrorEnum errorEnum,
      String response,
      Exception e) {
    log.warn(
        "[ALERT][{}][LAST_RETRY][DEAD-LETTER] Performed last retry for event ingestion: InvocationId [{}]",
        FN_NAME,
        context.getInvocationId());
    StorageAccountUtil.sendToErrorTable(context, blob, metadata, message, errorEnum, response, e);
  }
}
