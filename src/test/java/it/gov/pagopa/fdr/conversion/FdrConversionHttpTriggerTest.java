package it.gov.pagopa.fdr.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.fdr.conversion.model.BlobData;
import it.gov.pagopa.fdr.conversion.util.HttpResponseMessageMock;
import it.gov.pagopa.fdr.conversion.util.StorageAccountUtil;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class FdrConversionHttpTriggerTest {

  private static final String TEST_BLOB = "test-blob";
  @Mock private ExecutionContext mockContext;
  @Mock private FdrConversionBlobTrigger fdrConversionBlobTrigger;
  @Mock private HttpRequestMessage<Optional<String>> mockRequest;

  @InjectMocks private FdrConversionHttpTrigger function;

  @BeforeEach
  void setUp() {
    doAnswer(
            (Answer<HttpResponseMessage.Builder>)
                invocation -> {
                  HttpStatus status = (HttpStatus) invocation.getArguments()[0];
                  return new HttpResponseMessageMock.HttpResponseMessageBuilderMock()
                      .status(status);
                })
        .when(mockRequest)
        .createResponseBuilder(any(HttpStatus.class));
  }

  @Test
  void testOK() {
    // Mock static methods using mockStatic
    try (var mockedStorageUtil = mockStatic(StorageAccountUtil.class)) {
      // Mock the behavior of blob retrieval and processing
      BlobData mockBlobData = mock(BlobData.class);
      mockedStorageUtil
          .when(() -> StorageAccountUtil.getBlobContent(anyString()))
          .thenReturn(mockBlobData);
      byte[] content = "test".getBytes();
      HashMap<String, String> metadata = new HashMap<>();
      when(mockBlobData.getContent()).thenReturn(content);
      when(mockBlobData.getMetadata()).thenReturn(metadata);

      doReturn(true)
          .when(fdrConversionBlobTrigger)
          .process(content, TEST_BLOB, metadata, mockContext);

      HttpResponseMessage response = function.process(mockRequest, TEST_BLOB, mockContext);
      assertEquals(HttpStatus.OK, response.getStatus());
    }
  }

  @Test
  void testKO() {
    // Mock static methods using mockStatic
    try (var mockedStorageUtil = mockStatic(StorageAccountUtil.class)) {
      // Mock the behavior of blob retrieval and processing
      BlobData mockBlobData = mock(BlobData.class);
      mockedStorageUtil
          .when(() -> StorageAccountUtil.getBlobContent(anyString()))
          .thenReturn(mockBlobData);
      byte[] content = "test".getBytes();
      HashMap<String, String> metadata = new HashMap<>();
      when(mockBlobData.getContent()).thenReturn(content);
      when(mockBlobData.getMetadata()).thenReturn(metadata);

      doReturn(false)
          .when(fdrConversionBlobTrigger)
          .process(content, TEST_BLOB, metadata, mockContext);

      HttpResponseMessage response = function.process(mockRequest, TEST_BLOB, mockContext);
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }
  }

  @Test
  void testExceptionHandling() {
    // Mock static methods using mockStatic
    try (var mockedStorageUtil = mockStatic(StorageAccountUtil.class)) {
      // Simulate an exception in the method
      mockedStorageUtil
          .when(() -> StorageAccountUtil.getBlobContent(anyString()))
          .thenThrow(new RuntimeException("Test Exception"));

      HttpResponseMessage response = function.process(mockRequest, TEST_BLOB, mockContext);
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }
  }

  @Test
  void testBlobNull() {
    // Mock static methods using mockStatic
    try (var mockedStorageUtil = mockStatic(StorageAccountUtil.class)) {
      // Simulate an exception in the method
      mockedStorageUtil
          .when(() -> StorageAccountUtil.getBlobContent(anyString()))
          .thenReturn(null);

      HttpResponseMessage response = function.process(mockRequest, TEST_BLOB, mockContext);
      assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
    }
  }
}
