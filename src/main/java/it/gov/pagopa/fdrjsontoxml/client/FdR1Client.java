package it.gov.pagopa.fdrjsontoxml.client;

import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.ExponentialBackOff;
import it.gov.pagopa.fdrjsontoxml.exception.FdR4XXException;
import it.gov.pagopa.fdrjsontoxml.exception.FdR5XXException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class FdR1Client {
    private static FdR1Client instance = null;


    private final HttpTransport httpTransport = new NetHttpTransport();
    private final JsonFactory jsonFactory = new GsonFactory();
    private final String fdr1Host = System.getenv("FDR1_CLIENT_HOST"); // es: https://api.xxx.platform.pagopa.it
    private final String apiKey = System.getenv("FDR1_API_KEY");


    // Retry ExponentialBackOff config
    private final boolean enableRetry =
            System.getenv("ENABLE_CLIENT_RETRY") != null ? Boolean.parseBoolean(System.getenv("ENABLE_CLIENT_RETRY")) : Boolean.FALSE;
    private final int initialIntervalMillis =
            System.getenv("INITIAL_INTERVAL_MILLIS") != null ? Integer.parseInt(System.getenv("INITIAL_INTERVAL_MILLIS")) : 500;
    private final int maxElapsedTimeMillis =
            System.getenv("MAX_ELAPSED_TIME_MILLIS") != null ? Integer.parseInt(System.getenv("MAX_ELAPSED_TIME_MILLIS")) : 1000;
    private final int maxIntervalMillis  =
            System.getenv("MAX_INTERVAL_MILLIS") != null ? Integer.parseInt(System.getenv("MAX_INTERVAL_MILLIS")) : 1000;
    private final double multiplier  =
            System.getenv("MULTIPLIER") != null ? Double.parseDouble(System.getenv("MULTIPLIER")) : 1.5;
    private final double randomizationFactor  =
            System.getenv("RANDOMIZATION_FACTOR") != null ? Double.parseDouble(System.getenv("RANDOMIZATION_FACTOR")) : 0.5;

    public static FdR1Client getInstance() {
        if (instance == null) {
            instance = new FdR1Client();
        }
        return instance;
    }

    public void sendFdRJson(String data) throws IOException, IllegalArgumentException, FdR5XXException, FdR4XXException {

        GenericUrl url = new GenericUrl(fdr1Host);

        HttpRequest request = this.buildPostRequestToFdR1(url, data);

        if (enableRetry) {
            this.setRequestRetry(request);
        }

        executeCallToFdR1(request);
    }

    private HttpRequest buildPostRequestToFdR1(GenericUrl url, String data) throws IOException {

        HttpRequestFactory requestFactory = httpTransport.createRequestFactory(
                (HttpRequest request) ->
                        request.setParser(new JsonObjectParser(jsonFactory))
        );


        JsonHttpContent jsonHttpContent = new JsonHttpContent(jsonFactory, compress(data));
        HttpRequest request = requestFactory.buildPostRequest(url, jsonHttpContent);
        HttpHeaders headers = request.getHeaders();
        headers.set("Ocp-Apim-Subscription-Key", apiKey);
        return request;
    }

    private void setRequestRetry(HttpRequest request) {
        /*
         * Retry section config
         */
        ExponentialBackOff backoff = new ExponentialBackOff.Builder()
                .setInitialIntervalMillis(initialIntervalMillis)
                .setMaxElapsedTimeMillis(maxElapsedTimeMillis)
                .setMaxIntervalMillis(maxIntervalMillis)
                .setMultiplier(multiplier)
                .setRandomizationFactor(randomizationFactor)
                .build();

        // Exponential Backoff is turned off by default in HttpRequest -> it's necessary include an instance of HttpUnsuccessfulResponseHandler to the HttpRequest to activate it
        // The default back-off on anabnormal HTTP response is BackOffRequired.ON_SERVER_ERROR (5xx)
        request.setUnsuccessfulResponseHandler(
                new HttpBackOffUnsuccessfulResponseHandler(backoff));
    }

    private void executeCallToFdR1(HttpRequest request) throws IOException, IllegalArgumentException, FdR5XXException, FdR4XXException {
        try {
            request.execute();
        } catch (HttpResponseException e) {
            if (e.getStatusCode() / 100 == 4) {
                String message = String.format("Error %s calling the service URL %s", e.getStatusCode(), request.getUrl());
                throw new FdR4XXException(message);

            } else if (e.getStatusCode() / 100 == 5) {
                String message = String.format("Error %s calling the service URL %s", e.getStatusCode(), request.getUrl());
                throw new FdR5XXException(message);

            }
        }
    }

    private byte[] compress(String str) throws IOException {
        if ((str == null) || (str.isEmpty())) {
            return null;
        }
        ByteArrayOutputStream obj = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(obj);
        gzip.write(str.getBytes(StandardCharsets.UTF_8));
        gzip.flush();
        gzip.close();
        return obj.toByteArray();
    }

}
