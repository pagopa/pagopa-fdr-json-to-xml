package it.gov.pagopa.fdr.conversion;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class FdrConversionHttpTrigger {
    @FunctionName("ConversionFdr3Http")
    public void process (
            @HttpTrigger(name = "ConversionFdrHttpTrigger",
                    methods = {HttpMethod.POST},
                    route = "fdrs/{blob-name}/retry",
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        // todo
        // this function should read from the blob storage and send the request with the gzip file to FdR1
    }
}
