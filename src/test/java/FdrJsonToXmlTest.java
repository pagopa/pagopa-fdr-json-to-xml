import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.microsoft.azure.functions.ExecutionContext;
import it.gov.pagopa.fdr.conversion.exception.AppException;
import it.gov.pagopa.fdrjsontoxml.FdrJsonToXml;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openapitools.client.ApiException;
import org.openapitools.client.api.FdrFase1Api;
import org.openapitools.client.model.GenericResponse;
import org.powermock.reflect.Whitebox;
import util.TestUtil;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FdrJsonToXmlTest {

    @Spy
    FdrJsonToXml fdrJsonToXml;

    @Mock
    ExecutionContext context;

    private static final Logger logger = Logger.getLogger("FdrJsonToXml-test-logger");

    @Test
    @SneakyThrows
    void runOk() {
        when(context.getLogger()).thenReturn(logger);

        FdrFase1Api fdrFase1Api = mock(FdrFase1Api.class);

        Whitebox.setInternalState(FdrJsonToXml.class, "MAX_RETRY_COUNT", -1);
        Whitebox.setInternalState(FdrJsonToXml.class, "fdrFase1Api", fdrFase1Api);
        Whitebox.setInternalState(FdrJsonToXml.class, "tableName", "errors");

        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
        Whitebox.setInternalState(FdrJsonToXml.class, "tableServiceClient", tableServiceClient);

        GenericResponse genericResponse = new GenericResponse();
        genericResponse.setMessage("OK");
        when(fdrFase1Api.notifyFdrToConvert(any())).thenReturn(genericResponse);

        // generating input
        String message = new String((Base64
                .getEncoder()
                .encode(TestUtil.readStringFromFile("messages/fdr_ok.json")
                        .getBytes(StandardCharsets.UTF_8))));
        assertDoesNotThrow(() -> fdrJsonToXml.processNodoReEvent(message, context));
    }

    @Test
    @SneakyThrows
    void runKo_wrongMessage() {
        when(context.getLogger()).thenReturn(logger);

        FdrFase1Api fdrFase1Api = mock(FdrFase1Api.class);

        Whitebox.setInternalState(FdrJsonToXml.class, "MAX_RETRY_COUNT", -1);
        Whitebox.setInternalState(FdrJsonToXml.class, "fdrFase1Api", fdrFase1Api);
        Whitebox.setInternalState(FdrJsonToXml.class, "fdrFase1NewApiKey", UUID.randomUUID().toString());
        Whitebox.setInternalState(FdrJsonToXml.class, "fdrFase1BaseUrl", "http://localhost:8080");
        Whitebox.setInternalState(FdrJsonToXml.class, "tableName", "errors");

        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
        Whitebox.setInternalState(FdrJsonToXml.class, "tableServiceClient", tableServiceClient);
        TableClient tableClient = mock(TableClient.class);
        when(tableServiceClient.getTableClient(anyString())).thenReturn(tableClient);

        // generating input
        String message = new String((Base64
                .getEncoder()
                .encode(TestUtil.readStringFromFile("messages/fdr_ko.json")
                        .getBytes(StandardCharsets.UTF_8))));
        assertThrows(AppException.class, () -> fdrJsonToXml.processNodoReEvent(message, context));
    }

    @Test
    @SneakyThrows
    void runKo_genericError() {
        when(context.getLogger()).thenReturn(logger);

        FdrFase1Api fdrFase1Api = mock(FdrFase1Api.class);

        Whitebox.setInternalState(FdrJsonToXml.class, "MAX_RETRY_COUNT", -1);
        Whitebox.setInternalState(FdrJsonToXml.class, "fdrFase1Api", fdrFase1Api);
        Whitebox.setInternalState(FdrJsonToXml.class, "fdrFase1NewApiKey", UUID.randomUUID().toString());
        Whitebox.setInternalState(FdrJsonToXml.class, "fdrFase1BaseUrl", "http://localhost:8080");
        Whitebox.setInternalState(FdrJsonToXml.class, "tableName", "errors");

        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
        Whitebox.setInternalState(FdrJsonToXml.class, "tableServiceClient", tableServiceClient);
        TableClient tableClient = mock(TableClient.class);
        when(tableServiceClient.getTableClient(anyString())).thenReturn(tableClient);

        // generating input
        String message = Arrays.toString((Base64
                .getEncoder()
                .encode("".getBytes(StandardCharsets.UTF_8))));
        assertThrows(AppException.class, () -> fdrJsonToXml.processNodoReEvent(message, context));
    }

    @Test
    @SneakyThrows
    void runKo_pspApi_1() {
        when(context.getLogger()).thenReturn(logger);

        Whitebox.setInternalState(FdrJsonToXml.class, "MAX_RETRY_COUNT", -1);
        Whitebox.setInternalState(FdrJsonToXml.class, "fdrFase1NewApiKey", UUID.randomUUID().toString());
        Whitebox.setInternalState(FdrJsonToXml.class, "fdrFase1BaseUrl", "http://localhost:8080");
        Whitebox.setInternalState(FdrJsonToXml.class, "tableName", "errors");

        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
        Whitebox.setInternalState(FdrJsonToXml.class, "tableServiceClient", tableServiceClient);

        // generating input
        String message = new String((Base64
                .getEncoder()
                .encode(TestUtil.readStringFromFile("messages/fdr_ok.json")
                        .getBytes(StandardCharsets.UTF_8))));
        assertDoesNotThrow(() -> fdrJsonToXml.processNodoReEvent(message, context));
    }

    @Test
    @SneakyThrows
    void runKo_pspApi_2() {
        when(context.getLogger()).thenReturn(logger);

        FdrFase1Api fdrFase1Api = mock(FdrFase1Api.class);

        Whitebox.setInternalState(FdrJsonToXml.class, "MAX_RETRY_COUNT", -1);
        Whitebox.setInternalState(FdrJsonToXml.class, "fdrFase1Api", fdrFase1Api);
        Whitebox.setInternalState(FdrJsonToXml.class, "fdrFase1NewApiKey", UUID.randomUUID().toString());
        Whitebox.setInternalState(FdrJsonToXml.class, "fdrFase1BaseUrl", "http://localhost:8080");
        Whitebox.setInternalState(FdrJsonToXml.class, "tableName", "errors");

        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
        Whitebox.setInternalState(FdrJsonToXml.class, "tableServiceClient", tableServiceClient);
        TableClient tableClient = mock(TableClient.class);
        when(tableServiceClient.getTableClient(anyString())).thenReturn(tableClient);

        when(fdrFase1Api.notifyFdrToConvert(any())).thenThrow(new ApiException("Connection refused"));

        // generating input
        String message = new String((Base64
                .getEncoder()
                .encode(TestUtil.readStringFromFile("messages/fdr_ok.json")
                        .getBytes(StandardCharsets.UTF_8))));
        assertThrows(AppException.class, () -> fdrJsonToXml.processNodoReEvent(message, context));
    }
}
