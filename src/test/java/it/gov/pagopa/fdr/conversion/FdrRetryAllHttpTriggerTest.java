package it.gov.pagopa.fdr.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
import it.gov.pagopa.fdr.conversion.util.HttpResponseMessageMock;
import it.gov.pagopa.fdr.conversion.util.StorageAccountUtil;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class FdrRetryAllHttpTriggerTest {

  private static final String TEST_BLOB = "test-blob";

  @Mock private ExecutionContext mockContext;
  @Mock private FdrConversionBlobTrigger fdrConversionBlobTrigger;

  @Mock private HttpRequestMessage<Optional<String>> mockRequest;
  @Mock private BlobData mockBlobData;
  @Mock private PagedIterable<TableEntity> mockPagedIterable;
  @Mock private PagedResponse<TableEntity> mockPage;

  @InjectMocks private FdrRetryAllHttpTrigger function;

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
      Iterable<TableEntity> iterableFromStreamDirect = getTableEntities();

      mockedStorageUtil.when(StorageAccountUtil::getTableEntities).thenReturn(mockPagedIterable);
      when(mockPagedIterable.iterableByPage()).thenReturn(Collections.singletonList(mockPage));
      when(mockPage.getElements()).thenReturn(IterableStream.of(iterableFromStreamDirect));
      mockedStorageUtil
          .when(() -> StorageAccountUtil.getBlobContent(TEST_BLOB))
          .thenReturn(mockBlobData);
      byte[] content = "test".getBytes();
      HashMap<String, String> metadata = new HashMap<>();
      when(mockBlobData.getContent()).thenReturn(content);
      when(mockBlobData.getMetadata()).thenReturn(metadata);

      doReturn(true)
          .when(fdrConversionBlobTrigger)
          .process(content, TEST_BLOB, metadata, mockContext);

      HttpResponseMessage response = function.process(mockRequest, mockContext);

      assertEquals(HttpStatus.OK, response.getStatus());
    }
  }

  @Test
  void testBlobNotProcessed() {
    // Mock static methods using mockStatic
    try (var mockedStorageUtil = mockStatic(StorageAccountUtil.class)) {
      // Mock the behavior of blob retrieval and processing
      Iterable<TableEntity> iterableFromStreamDirect = getTableEntities();

      mockedStorageUtil.when(StorageAccountUtil::getTableEntities).thenReturn(mockPagedIterable);
      when(mockPagedIterable.iterableByPage()).thenReturn(Collections.singletonList(mockPage));
      when(mockPage.getElements()).thenReturn(IterableStream.of(iterableFromStreamDirect));
      mockedStorageUtil
          .when(() -> StorageAccountUtil.getBlobContent(TEST_BLOB))
          .thenReturn(mockBlobData);
      byte[] content = "test".getBytes();
      HashMap<String, String> metadata = new HashMap<>();
      when(mockBlobData.getContent()).thenReturn(content);
      when(mockBlobData.getMetadata()).thenReturn(metadata);

      doReturn(false)
          .when(fdrConversionBlobTrigger)
          .process(content, TEST_BLOB, metadata, mockContext);

      HttpResponseMessage response = function.process(mockRequest, mockContext);

      assertEquals(HttpStatus.OK, response.getStatus());
    }
  }

  @Test
  void testExceptionHandling() {
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

  @Test
  void testBlobNull() {
    // Mock static methods using mockStatic
    try (var mockedStorageUtil = mockStatic(StorageAccountUtil.class)) {
      // Simulate an exception in the method
      Iterable<TableEntity> iterableFromStreamDirect = getTableEntities();

      mockedStorageUtil.when(StorageAccountUtil::getTableEntities).thenReturn(mockPagedIterable);
      when(mockPagedIterable.iterableByPage()).thenReturn(Collections.singletonList(mockPage));
      when(mockPage.getElements()).thenReturn(IterableStream.of(iterableFromStreamDirect));
      mockedStorageUtil.when(() -> StorageAccountUtil.getBlobContent(TEST_BLOB)).thenReturn(null);

      HttpResponseMessage response = function.process(mockRequest, mockContext);

      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatus());
    }
  }

  private Iterable<TableEntity> getTableEntities() {
    TableEntity mockedEntity = new TableEntity("partitionKey", "rowKey");
    mockedEntity.addProperty("blob", TEST_BLOB);
    return () -> Stream.of(mockedEntity).iterator();
  }
}
