package functions;

import com.microsoft.azure.functions.ExecutionContext;
import feign.FeignException;
import it.gov.pagopa.fdr.conversion.FdrConversionBlobTrigger;
import it.gov.pagopa.fdr.conversion.exception.AlertAppException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpResponse;
import shaded_package.org.apache.commons.io.IOUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.model.HttpRequest.request;
import static util.Utils.createContext;

@ExtendWith(MockitoExtension.class)
public class FdrConversionBlobTriggerTest {

    private static final String TEST_URL = "http://localhost:8080";
    private ExecutionContext context;

    FdrConversionBlobTrigger fdrConversionFunction = new FdrConversionBlobTrigger();

    @BeforeAll
    static void beforeAll() {
        startClientAndServer(8080);
    }

    @BeforeEach
    void beforeEach() {
        context = createContext(1);
    }

    @Test
    void processOk() {
        byte[] content = "test".getBytes();
        createMockClient(200);
        fdrConversionFunction.FDR_FASE1_BASE_URL = TEST_URL;
        boolean processResult = fdrConversionFunction
                .process(content, "blob-name-1", Map.of("elaborate", "true"), context);
        Assertions.assertTrue(processResult);
    }

    @Test
    void processFail() {
        byte[] content = "test".getBytes();
        createMockClient(500);
        fdrConversionFunction.FDR_FASE1_BASE_URL = TEST_URL;
        Assertions.assertThrows(FeignException.class, () -> fdrConversionFunction.process(content, "blob-name-1",
                Map.of("elaborate", "true"), context));
    }

    @Test
    void retryTest() {
        context = createContext(9);
        byte[] content = "todo".getBytes();
        createMockClient(500);
        fdrConversionFunction.FDR_FASE1_BASE_URL = TEST_URL;
        Assertions.assertThrows(AlertAppException.class, () -> fdrConversionFunction.process(content, "blob-name-1",
                Map.of("elaborate", "true"), context));
    }

    @Test
    void payloadGenerationTest() throws IOException {
        // test base64 content (.zip file) codification and the JSON conversion (expected JSON file)
        InputStream is = new FileInputStream("./src/test/resources/test-1-content.json.zip");
        byte[] content = IOUtils.toByteArray(is);
        String actualPayload = FdrConversionBlobTrigger.getPayload(content);
        Path expectedPath = Path.of("./src/test/resources/test-1-expected.json");
        String expectedPayload = Files.readString(expectedPath);

        Assertions.assertEquals(expectedPayload, actualPayload);
    }

    @Test
    void alertAppExceptionTest() {
        RuntimeException e = new RuntimeException();
        Exception alertAppException = new AlertAppException("message", e.getCause(), "details");
        String expected = """
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
                                .withHeader("Content-Type", "application/json"), exactly(1))
                .respond(request -> {
                            // Extract request body
                            String requestBody = request.getBodyAsString();
                            System.out.println("Request body: " + requestBody);
                            System.out.println("Full received request: " + request);
                            return HttpResponse.response()
                                    .withStatusCode(status)
                                    .withBody("File received and processed successfully!");
                        });
    }
}
