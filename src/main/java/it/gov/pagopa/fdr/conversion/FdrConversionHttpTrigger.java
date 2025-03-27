package it.gov.pagopa.fdr.conversion;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.fdr.conversion.model.BlobData;
import it.gov.pagopa.fdr.conversion.util.StorageAccountUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class FdrConversionHttpTrigger {
    @FunctionName("FDR3ErrorRetry")
    public HttpResponseMessage process (
            @HttpTrigger(name = "ConversionFdrHttpTrigger",
                    methods = {HttpMethod.POST},
                    route = "errors/{blobName}/retry",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @BindingName("blobName") String blobName,
            final ExecutionContext context) {
        // this function should read from the blob storage and send the request with the gzip file to FdR1
        BlobData blobData = StorageAccountUtil.getBlobContent(blobName);
        FdrConversionBlobTrigger processor = new FdrConversionBlobTrigger();
        boolean processed = processor.process(blobData.getContent(), blobName, blobData.getMetadata(), context);
        if(processed){
            return request.createResponseBuilder(HttpStatus.OK).body(HttpStatus.OK.toString()).build();
        } else {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body(HttpStatus.INTERNAL_SERVER_ERROR.toString()).build();
        }
    }
}
