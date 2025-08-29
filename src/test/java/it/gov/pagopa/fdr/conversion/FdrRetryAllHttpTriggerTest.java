package it.gov.pagopa.fdr.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.PagedResponse;
import com.azure.core.util.IterableStream;
import com.azure.data.tables.models.TableEntity;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.fdr.conversion.model.BlobData;
import it.gov.pagopa.fdr.conversion.util.StorageAccountUtil;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FdrRetryAllHttpTriggerTest {

  private final AtomicReference<HttpStatus> statusToReturn = new AtomicReference<>();

  @Mock private ExecutionContext mockContext;
  @Mock private HttpRequestMessage<Optional<String>> mockRequest;

  private FdrRetryAllHttpTrigger function;
  private HttpResponseMessage.Builder mockResponseBuilder;
  private HttpResponseMessage mockResponse;

  @BeforeEach
  void setUp() {
    function = new FdrRetryAllHttpTrigger();
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
      BlobData mockBlobData = mock(BlobData.class); // Mock PagedIterable<TableEntity>
      PagedIterable<TableEntity> mockPagedIterable = mock(PagedIterable.class);
      PagedResponse<TableEntity> mockPage = mock(PagedResponse.class);
      TableEntity mockedEntity = new TableEntity("partitionKey", "rowKey");
      mockedEntity.addProperty("propertyName", "propertyValue");
      Iterable<TableEntity> iterableFromStreamDirect = () -> Stream.of(mockedEntity).iterator();

      when(mockPage.getElements()).thenReturn(IterableStream.of(iterableFromStreamDirect));
      when(mockPagedIterable.iterableByPage()).thenReturn(Collections.singletonList(mockPage));
      mockedStorageUtil
          .when(() -> StorageAccountUtil.getBlobContent(anyString()))
          .thenReturn(mockBlobData);
      mockedStorageUtil.when(StorageAccountUtil::getTableEntities).thenReturn(mockPagedIterable);

      HttpResponseMessage response = function.process(mockRequest, mockContext);
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
      mockedStorageUtil
          .when(() -> StorageAccountUtil.getBlobContent(anyString()))
          .thenReturn(mockBlobData);

      HttpResponseMessage response = function.process(mockRequest, mockContext);
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }
  }

  @Test
  void testExceptionHandling() {
    statusToReturn.set(HttpStatus.INTERNAL_SERVER_ERROR);

    // Mock static methods using mockStatic
    try (var mockedStorageUtil = mockStatic(StorageAccountUtil.class)) {
      // Simulate an exception in the method
      mockedStorageUtil
          .when(StorageAccountUtil::getTableEntities)
          .thenThrow(new RuntimeException("Test Exception"));

      HttpResponseMessage response = function.process(mockRequest, mockContext);
      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }
  }
}
