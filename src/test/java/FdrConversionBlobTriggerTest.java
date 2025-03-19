import com.microsoft.azure.functions.ExecutionContext;
import feign.FeignException;
import it.gov.pagopa.fdr.conversion.FdrConversionBlobTrigger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.HttpResponse;

import java.util.HashMap;
import java.util.logging.Logger;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.matchers.Times.exactly;
import static org.mockserver.model.HttpRequest.request;

@ExtendWith(MockitoExtension.class)
public class FdrConversionBlobTriggerTest {

    private static final String TEST_URL = "http://localhost:8080";
    private static final ExecutionContext context = createContext();

    @Spy
    FdrConversionBlobTrigger fdrConversionFunction;

    @BeforeAll
    static void beforeAll() {
        startClientAndServer(8080);
    }

    @Test
    void processOk() {
        byte[] content = "todo".getBytes();
        createMockClient(200);
        fdrConversionFunction.setFdrFase1BaseUrl(TEST_URL);
        fdrConversionFunction.process(content, "blob-name-1", new HashMap<>(), context);
    }

    @Test
    void processFail() {
        byte[] content = "todo".getBytes();
        createMockClient(500);
        fdrConversionFunction.setFdrFase1BaseUrl(TEST_URL);
        Assertions.assertThrows(FeignException.class, () -> fdrConversionFunction.process(content, "blob-name-1", new HashMap<>(), context));
    }

    public static void createMockClient(Integer status) {
        new MockServerClient("127.0.0.1", 8080)
                .when(
                        request()
                                .withMethod("POST")
                                .withPath("/conversion/fdr3")
                                .withHeader("Content-Encoding", "gzip"), exactly(1))
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

    private static ExecutionContext createContext() {
        return new ExecutionContext() {
            @Override
            public Logger getLogger() {
                return  Logger.getLogger("test");
            }

            @Override
            public String getInvocationId() {
                return "test-invocation-id";
            }

            @Override
            public String getFunctionName() {
                return "test-function-name";
            }
        };
    }
}
