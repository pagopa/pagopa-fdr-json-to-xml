package it.gov.pagopa.fdr.conversion;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.fdr.conversion.model.BlobData;
import it.gov.pagopa.fdr.conversion.util.StorageAccountUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.logging.Logger;

import static it.gov.pagopa.fdr.conversion.util.StorageAccountUtil.removeEntity;

@Slf4j
public class FdrConversionHttpTrigger {
    @FunctionName("ErrorRetryFunction")
    public HttpResponseMessage process (
            @HttpTrigger(name = "ErrorRetryFunctionHttpTrigger",
                    methods = {HttpMethod.GET},
                    route = "errors/{blobName}/retry",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @BindingName("blobName") String blobName,
            final ExecutionContext context) {
        Logger logger = context.getLogger() == null ? Logger.getLogger("ErrorRetryFunction") : context.getLogger();
        try {
            // this function should read from the blob storage and send the request with the gzip file to FdR1
            BlobData blobData = StorageAccountUtil.getBlobContent(blobName);
            if(blobData == null) {
                return request.createResponseBuilder(HttpStatus.NOT_FOUND).body(HttpStatus.NOT_FOUND.toString()).build();
            }
            FdrConversionBlobTrigger processor = new FdrConversionBlobTrigger();
            boolean processed = processor.process(blobData.getContent(), blobName, blobData.getMetadata(), context);
            if (processed) {
                removeEntity(blobData.getMetadata());
                return request.createResponseBuilder(HttpStatus.OK).body(HttpStatus.OK.toString()).build();
            } else {
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body(HttpStatus.INTERNAL_SERVER_ERROR.toString()).build();
            }
        } catch (Exception e) {
            logger.severe(String.format("[Exception][id=%s] Error while FDR3 ErrorRetryFunction execution, class = %s, message = %s",
                    context.getInvocationId(), e.getClass(), e.getMessage()));
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body(HttpStatus.INTERNAL_SERVER_ERROR.toString()).build();
        }
    }
}
