package it.gov.pagopa.fdr.conversion.exception;

import com.google.gson.JsonObject;
import lombok.Getter;

/**
 * Custom exception with trigger details and last retry info
 */
@Getter
public class AlertAppException extends RuntimeException {
    public static final String FDR3_FLOW_BLOB_CONTAINER_NAME = System.getenv("BLOB_STORAGE_FDR3_CONTAINER");
    public static final String EXCEPTION_BLOB_NAME = "blobName";
    public static final String EXCEPTION_BLOB_CONTAINER = "blobContainer";
    public static final String EXCEPTION_BLOB_SESSION_ID = "blobSessionId";
    public static final String EXCEPTION_RETRY_INDEX = "retryIndex";
    private final String details;

    public AlertAppException(String message, Throwable cause, String details) {
        super(message, cause);
        this.details = details;
    }

    @Override
    public String toString() {
        return super.toString() + "\nDetails:" + details;
    }

    public static String getExceptionDetails(String blob, String sessionId, int retry) {
        JsonObject jsonDetails = new JsonObject();
        jsonDetails.addProperty(EXCEPTION_BLOB_NAME, blob);
        jsonDetails.addProperty(EXCEPTION_BLOB_CONTAINER, FDR3_FLOW_BLOB_CONTAINER_NAME);
        jsonDetails.addProperty(EXCEPTION_BLOB_SESSION_ID, sessionId);
        jsonDetails.addProperty(EXCEPTION_RETRY_INDEX, retry);

        return jsonDetails.toString();
    }
}