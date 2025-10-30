package it.gov.pagopa.fdr.conversion.client;

import feign.Headers;
import feign.Param;
import feign.RequestLine;

public interface FdR1Client {

    // Call POST /convert/fdr3 with byte array
    @RequestLine("POST /convert/fdr3")
    @Headers({
            "Content-Type: application/zip",
            "Ocp-Apim-Subscription-Key: {subscriptionKey}"
    })
    void postConversion(@Param("subscriptionKey") String subscriptionKey, byte[] payload);
}