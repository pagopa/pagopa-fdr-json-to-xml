package it.gov.pagopa.fdr.conversion.client;

import feign.Headers;
import feign.Param;
import feign.RequestLine;

public interface FdR1Client {

    // Call POST /conversion/fdr3 with the required headers for GZIP
    @RequestLine("POST /conversion/fdr3")
    @Headers({
            "Content-Encoding: gzip",
            "Ocp-Apim-Subscription-Key: {subscriptionKey}"
    })
    void postGzipFile(@Param("subscriptionKey") String subscriptionKey, byte[] gzipFile);
}