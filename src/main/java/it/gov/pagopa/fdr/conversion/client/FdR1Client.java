package it.gov.pagopa.fdr.conversion.client;

import feign.Headers;
import feign.Param;
import feign.RequestLine;

public interface FdR1Client {

    // Call POST /convert/fdr3 with the JSON body
    @RequestLine("POST /convert/fdr3")
    @Headers({
            "Content-Type: application/json",
            "Ocp-Apim-Subscription-Key: {subscriptionKey}"
    })
    void postConversion(@Param("subscriptionKey") String subscriptionKey, String payload);
}