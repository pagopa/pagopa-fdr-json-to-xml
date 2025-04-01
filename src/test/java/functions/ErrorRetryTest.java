package functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;

import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import it.gov.pagopa.fdr.conversion.FdrConversionHttpTrigger;

import it.gov.pagopa.fdr.conversion.model.BlobData;
import it.gov.pagopa.fdr.conversion.util.StorageAccountUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ErrorRetryTest {

    private final AtomicReference<HttpStatus> statusToReturn = new AtomicReference<>();

    @Mock private ExecutionContext mockContext;
    @Mock private HttpRequestMessage<Optional<String>> mockRequest;

    private FdrConversionHttpTrigger function;
    private HttpResponseMessage.Builder mockResponseBuilder;
    private HttpResponseMessage mockResponse;

    @BeforeEach
    void setUp() {
        function = new FdrConversionHttpTrigger();
        Logger logger = mock(Logger.class);
        lenient().when(mockContext.getLogger()).thenReturn(logger);

        mockResponseBuilder = mock(HttpResponseMessage.Builder.class);
        mockResponse = mock(HttpResponseMessage.class);

        lenient()
                .when(mockResponseBuilder.header(anyString(), anyString()))
                .thenReturn(mockResponseBuilder);
        lenient().when(mockResponseBuilder.body(any())).thenReturn(mockResponseBuilder);
        lenient()
                .when(mockResponseBuilder.build())
                .thenAnswer(
                        invocation -> {
                            when(mockResponse.getStatus()).thenReturn(statusToReturn.get());
                            return mockResponse;
                        });

        lenient()
                .when(mockRequest.createResponseBuilder(any(HttpStatus.class)))
                .thenReturn(mockResponseBuilder);
    }

    @Test
    void testOK() {
        statusToReturn.set(HttpStatus.OK);

        // Mock static methods using mockStatic
        try (var mockedStorageUtil = mockStatic(StorageAccountUtil.class)) {
            // Mock the behavior of blob retrieval and processing
            BlobData mockBlobData = mock(BlobData.class);
            mockedStorageUtil.when(() -> StorageAccountUtil.getBlobContent(anyString())).thenReturn(mockBlobData);
            when(mockBlobData.getContent()).thenReturn("test".getBytes());
            when(mockBlobData.getMetadata()).thenReturn(new HashMap<>());

            HttpResponseMessage response = function.process(mockRequest, "test-blob", mockContext);
            assertEquals(HttpStatus.OK, response.getStatus());
        }
    }

    @Test
    void testKO() {
        statusToReturn.set(HttpStatus.INTERNAL_SERVER_ERROR);

        // Mock static methods using mockStatic
        try (var mockedStorageUtil = mockStatic(StorageAccountUtil.class)) {
            // Mock the behavior of blob retrieval and processing
            BlobData mockBlobData = mock(BlobData.class);
            mockedStorageUtil.when(() -> StorageAccountUtil.getBlobContent(anyString())).thenReturn(mockBlobData);
            when(mockBlobData.getContent()).thenReturn("test".getBytes());
            when(mockBlobData.getMetadata()).thenReturn(new HashMap<>());

            HttpResponseMessage response = function.process(mockRequest, "test-blob", mockContext);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
        }
    }

    @Test
    void testExceptionHandling() {
        statusToReturn.set(HttpStatus.INTERNAL_SERVER_ERROR);

        // Mock static methods using mockStatic
        try (var mockedStorageUtil = mockStatic(StorageAccountUtil.class)) {
            // Simulate an exception in the method
            mockedStorageUtil.when(() -> StorageAccountUtil.getBlobContent(anyString())).thenThrow(new RuntimeException("Test Exception"));

            HttpResponseMessage response = function.process(mockRequest, "test-blob", mockContext);
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
        }
    }
}
