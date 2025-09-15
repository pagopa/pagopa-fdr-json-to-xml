package it.gov.pagopa.fdr.conversion;

import static it.gov.pagopa.fdr.conversion.util.StorageAccountUtil.removeEntity;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.fdr.conversion.model.BlobData;
import it.gov.pagopa.fdr.conversion.util.StorageAccountUtil;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FdrConversionHttpTrigger {

  private final FdrConversionBlobTrigger processor;

  public FdrConversionHttpTrigger(FdrConversionBlobTrigger processor) {
    this.processor = processor;
  }

  public FdrConversionHttpTrigger() {
    this.processor = new FdrConversionBlobTrigger();
  }

  @FunctionName("ErrorRetryFunction")
  public HttpResponseMessage process(
      @HttpTrigger(
              name = "ErrorRetryFunctionHttpTrigger",
              methods = {HttpMethod.GET},
              route = "errors/{blobName}/retry",
              authLevel = AuthorizationLevel.ANONYMOUS)
          HttpRequestMessage<Optional<String>> request,
      @BindingName("blobName") String blobName,
      final ExecutionContext context) {
    try {
      // this function should read from the blob storage and send the request with the gzip file to
      // FdR1
      BlobData blobData = StorageAccountUtil.getBlobContent(blobName);
      if (blobData == null) {
        return request
            .createResponseBuilder(HttpStatus.NOT_FOUND)
            .body(HttpStatus.NOT_FOUND.toString())
            .build();
      }

      boolean processed =
          this.processor.process(blobData.getContent(), blobName, blobData.getMetadata(), context);
      if (processed) {
        removeEntity(context, blobData.getMetadata());
        return request.createResponseBuilder(HttpStatus.OK).body(HttpStatus.OK.toString()).build();
      } else {
        return request
            .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(HttpStatus.INTERNAL_SERVER_ERROR.toString())
            .build();
      }
    } catch (Exception e) {
      log.error(
          "[Exception][id={}] Error while FDR3 ErrorRetryFunction execution, class = {}, message = {}",
          context.getInvocationId(),
          e.getClass(),
          e.getMessage());
      return request
          .createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(HttpStatus.INTERNAL_SERVER_ERROR.toString())
          .build();
    }
  }
}
