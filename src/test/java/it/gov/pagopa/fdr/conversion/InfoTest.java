package it.gov.pagopa.fdr.conversion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import it.gov.pagopa.fdr.conversion.model.AppInfo;
import it.gov.pagopa.fdr.conversion.util.HttpResponseMessageMock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class InfoTest {

  @Mock ExecutionContext executionContextMock;

  @Spy Info sut;

  @Test
  void runOK() {
    @SuppressWarnings("unchecked")
    HttpRequestMessage<Optional<String>> request = mock(HttpRequestMessage.class);

    doAnswer(
            (Answer<HttpResponseMessage.Builder>)
                invocation -> {
                  HttpStatus status = (HttpStatus) invocation.getArguments()[0];
                  return new HttpResponseMessageMock.HttpResponseMessageBuilderMock()
                      .status(status);
                })
        .when(request)
        .createResponseBuilder(any(HttpStatus.class));

    // test execution
    HttpResponseMessage response = sut.run(request, executionContextMock);

    // test assertion
    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatus());
    assertNotNull(response.getBody());
    AppInfo responseBody = (AppInfo) response.getBody();
    assertNotNull(responseBody.getName());
    assertNotNull(responseBody.getVersion());
    assertNotNull(responseBody.getEnvironment());
    assertEquals("FdR-JSON-to-XML-Function", responseBody.getName());
    assertEquals("azure-fn", responseBody.getEnvironment());
  }
}
