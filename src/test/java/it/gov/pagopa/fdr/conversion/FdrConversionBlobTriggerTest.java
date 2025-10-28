package it.gov.pagopa.fdr.conversion;

import static it.gov.pagopa.fdr.conversion.util.Utils.createContext;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.model.HttpRequest.request;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.specialized.BlobInputStream;
import com.microsoft.azure.functions.ExecutionContext;
import feign.Feign;
import feign.FeignException;
import it.gov.pagopa.fdr.conversion.client.AppInsightTelemetryClient;
import it.gov.pagopa.fdr.conversion.client.FdR1Client;
import it.gov.pagopa.fdr.conversion.exception.AlertAppException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpResponse;


@ExtendWith(MockitoExtension.class)
public class FdrConversionBlobTriggerTest {

  private static final String TEST_URL = "http://localhost:8080";
  private static final Map<String, String> METADATA = Map.of("elaborate", "true");
  private ExecutionContext context;

  private BlobServiceClient blobServiceClient;
  private AppInsightTelemetryClient aiTelemetryClientMock;
  private FdrConversionBlobTrigger sut;

  @BeforeAll
  static void beforeAll() {
    startClientAndServer(8080);
  }

  @BeforeEach
  void beforeEach() {
    blobServiceClient = Mockito.mock(BlobServiceClient.class);
    aiTelemetryClientMock = Mockito.mock(AppInsightTelemetryClient.class);
    sut =
        new FdrConversionBlobTrigger(
                blobServiceClient,
                Feign.builder().target(FdR1Client.class, TEST_URL),
                aiTelemetryClientMock);
    context = createContext(1);


  }

  @Test
  void processOk() throws IOException {
    byte[] content = "test".getBytes();
    mockBlobClient(content);
    createMockClient(200);

    boolean processResult =
        assertDoesNotThrow(() -> sut.process(content, "blob-name-1", METADATA, context));

    assertTrue(processResult);

    verify(aiTelemetryClientMock, never()).createCustomEventForAlert(anyString(), any());
  }

  @Test
  void processFail() {
    byte[] content = "test".getBytes();
    mockBlobClient(content);
    createMockClient(500);

    assertThrows(
        FeignException.class, () -> sut.process(content, "blob-name-1", METADATA, context));

    verify(aiTelemetryClientMock, never()).createCustomEventForAlert(anyString(), any());
  }

  @Test
  void retryTest() {
    context = createContext(9);
    byte[] content = "todo".getBytes();
    createMockClient(500);

    assertThrows(
        AlertAppException.class, () -> sut.process(content, "blob-name-1", METADATA, context));

    verify(aiTelemetryClientMock).createCustomEventForAlert(anyString(), any());
  }

  @Test
  void payloadGenerationTest() throws IOException {
    // test base64 content (.zip file) codification and the JSON conversion (expected JSON file)
    InputStream is = new FileInputStream("./src/test/resources/test-1-content.json.zip");
//    byte[] content = IOUtils.toByteArray(is);
    String actualPayload = FdrConversionBlobTrigger.getPayload(is);
    Path expectedPath = Path.of("./src/test/resources/test-1-expected.json");
    String expectedPayload = Files.readString(expectedPath);

    Assertions.assertEquals(expectedPayload, actualPayload);
  }

  @Test
  void alertAppExceptionTest() {
    RuntimeException e = new RuntimeException();
    Exception alertAppException = new AlertAppException("message", e.getCause(), "details");
    String expected =
"""
[ALERT][FdrJsonToXml][LAST_RETRY][class it.gov.pagopa.fdr.conversion.exception.AlertAppException]:details=details,
message=it.gov.pagopa.fdr.conversion.exception.AlertAppException: message""";
    Assertions.assertEquals(expected, alertAppException.toString());
  }

  public static void createMockClient(Integer status) {
    new MockServerClient("127.0.0.1", 8080)
        .when(
            request()
                .withMethod("POST")
                .withPath("/convert/fdr3")
                .withHeader("Content-Type", "application/json"),
            exactly(1))
        .respond(
            request -> {
              // Extract request body
              String requestBody = request.getBodyAsString();
              System.out.println("Request body: " + requestBody);
              System.out.println("Full received request: " + request);
              return HttpResponse.response()
                  .withStatusCode(status)
                  .withBody("File received and processed successfully!");
            });
  }

  private void mockBlobClient(byte[] content) {
    BlobContainerClient blobContainerClient = Mockito.mock(BlobContainerClient.class);
    BlobClient blobClient = Mockito.mock(BlobClient.class);
    BlobInputStream mockBlobInputStream = Mockito.mock(BlobInputStream.class);
    Mockito.when(blobServiceClient.getBlobContainerClient(any())).thenReturn(blobContainerClient);
    Mockito.when(blobContainerClient.getBlobClient(anyString())).thenReturn(blobClient);
    Mockito.when(blobClient.openInputStream()).thenReturn(mockBlobInputStream);
    Mockito.doNothing().when(mockBlobInputStream).close();
  }
}
