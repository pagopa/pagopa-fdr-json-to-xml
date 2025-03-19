import com.azure.core.http.rest.PagedIterable;
import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.models.TableEntity;
import com.azure.storage.queue.QueueClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.fdr.conversion.util.AppConstant;
import it.gov.pagopa.fdrjsontoxml.FdrJsonError;
import it.gov.pagopa.fdrjsontoxml.FdrMessage;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.powermock.reflect.Whitebox;

import java.util.*;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FdrJsonErrorTest {

    @Spy
    FdrJsonError fdrJsonError;

    @Mock
    ExecutionContext context;

    private static final Logger logger = Logger.getLogger("FdrJsonError-test-logger");

    @Test
    @SneakyThrows
    void runOk() {
        // mocking objects
        when(context.getLogger()).thenReturn(logger);

        HttpRequestMessage request = mock(HttpRequestMessage.class);
        HttpResponseMessage.Builder builder = mock(HttpResponseMessage.Builder.class);

        doReturn(builder).when(request).createResponseBuilder(any(HttpStatus.class));

        Whitebox.setInternalState(FdrJsonError.class, "tableName", "errors");
        TableServiceClient tableServiceClient = mock(TableServiceClient.class);
        Whitebox.setInternalState(FdrJsonError.class, "tableServiceClient", tableServiceClient);
        TableClient tableClient = mock(TableClient.class);
        PagedIterable<TableEntity> pagedIterable = mock(PagedIterable.class);
        when(tableServiceClient.getTableClient(anyString())).thenReturn(tableClient);
        when(tableClient.listEntities()).thenReturn(pagedIterable);
        TableEntity tableEntity = new TableEntity("2024-01-01", "1");
        Map<String, Object> tableEntityMap = new HashMap<>();

        FdrMessage fdrMessage = new FdrMessage();
        fdrMessage.setFdr(UUID.randomUUID().toString());
        fdrMessage.setRetry(1L);
        fdrMessage.setRevision(1L);
        fdrMessage.setPspId("60000000001");
        fdrMessage.setOrganizationId("01234567891");
        tableEntityMap.put(AppConstant.columnFieldMessage, new ObjectMapper().writeValueAsString(fdrMessage));
        tableEntity.setProperties(tableEntityMap);
        Iterator<TableEntity> mockIterator = List.of(tableEntity).iterator();
        when(pagedIterable.iterator()).thenReturn(mockIterator);

        QueueClient queueClient = mock(QueueClient.class);
        Whitebox.setInternalState(FdrJsonError.class, "queueClient", queueClient);

        // generating input
        request.getQueryParameters().put("partitionKey", "2024-01-01");
        request.getQueryParameters().put("rowKey", "1");
        request.getQueryParameters().put("deleteOnlyByKey", "true");

        // execute logic
        Assertions.assertThrows(Exception.class, () -> fdrJsonError.run(request, context));
    }
}
