package it.gov.pagopa.fdrjsontoxml;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.ExponentialBackoffRetry;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
import it.gov.pagopa.fdr.conversion.exception.AppException;
import it.gov.pagopa.fdr.conversion.util.AppConstant;
import it.gov.pagopa.fdr.conversion.util.ErrorEnum;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openapitools.client.ApiClient;
import org.openapitools.client.ApiException;
import org.openapitools.client.api.FdrFase1Api;
import org.openapitools.client.model.Fdr;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Azure Functions with Azure Queue trigger.
 */
public class FdrJsonToXml {

	private static final Integer MAX_RETRY_COUNT = 10;

	private static final String fdrFase1BaseUrl = System.getenv("FDR_FASE1_BASE_URL");
	private static final String fdrFase1NewApiKey = System.getenv("FDR_FASE1_API_KEY");

	private static final String tableName = System.getenv("TABLE_STORAGE_TABLE_NAME");
	private static final String tableStorageConnString = System.getenv("TABLE_STORAGE_CONN_STRING");

	private static FdrFase1Api fdrFase1Api = null;
	private static TableServiceClient tableServiceClient = null;

	private static FdrFase1Api getFdrFase1Api(){
		if(fdrFase1Api==null){
			ApiClient apiClient = new ApiClient();
			apiClient.setApiKey(fdrFase1NewApiKey);
			fdrFase1Api = new FdrFase1Api(apiClient);
			fdrFase1Api.setCustomBaseUrl(fdrFase1BaseUrl);
		}
		return fdrFase1Api;
	}

	private static TableServiceClient getTableServiceClient(){
		if(tableServiceClient==null){
			tableServiceClient = new TableServiceClientBuilder()
					.connectionString(tableStorageConnString)
					.buildClient();
			tableServiceClient.createTableIfNotExists(tableName);
		}
		return tableServiceClient;
	}


    @FunctionName("QueueFdrJsonToXmlEventProcessor")
	@ExponentialBackoffRetry(maxRetryCount = 10, maximumInterval = "01:30:00", minimumInterval = "00:00:10")
    public void processNodoReEvent (
			@QueueTrigger(
					name = "jsonTrigger",
					queueName = "flowidsendqueue",
					connection = "STORAGE_ACCOUNT_CONN_STRING")
			String b64Message,
			final ExecutionContext context) {

		String errorCause = null;
		boolean isPersistenceOk = true;
		Throwable causedBy = null;
		int retryIndex = context.getRetryContext() == null ? -1 : context.getRetryContext().getRetrycount();

		Logger logger = context.getLogger();
		if (retryIndex == MAX_RETRY_COUNT) {
			logger.log(Level.WARNING, () -> String.format("[ALERT][FdrJsonToXml][LAST_RETRY] Performing last retry for event ingestion: InvocationId [%s]", context.getInvocationId()));
		}

		String message = "";
		try {
			message = new String(Base64.getDecoder().decode(b64Message), StandardCharsets.UTF_8);
			
			// read json
			ObjectMapper objectMapper = new ObjectMapper();
			FdrMessage fdrMessage = objectMapper.readValue(message, FdrMessage.class);
			logger.log(Level.INFO, () -> String.format("Performing event ingestion: InvocationId [%s], Retry Attempt [%d], File name:[%s]", context.getInvocationId(), retryIndex, fdrMessage.getFdr()));

			// create body for notify FDR
			Fdr fdr = getFdr(fdrMessage);

			// call notify FDR
			manageHttpError(message, () ->
					getFdrFase1Api().notifyFdrToConvert(fdr)
			);

		} catch (JsonProcessingException e) {
			logger.log(Level.INFO, () -> String.format("Performing event ingestion: InvocationId [%s], Retry Attempt [%d], File name:[UNKNOWN]", context.getInvocationId(), retryIndex));

			Instant now = Instant.now();
			ErrorEnum error = ErrorEnum.GENERIC_ERROR;
			sendGenericError(now, message, error, e);

			isPersistenceOk = false;
			errorCause = getErrorMessage(error, message, now);
			causedBy = e;
		} catch (AppException e) {
			isPersistenceOk = false;
			errorCause = e.getMessage();
			causedBy = e;
		} catch (Exception e) {
			Instant now = Instant.now();
			ErrorEnum error = ErrorEnum.GENERIC_ERROR;
			sendGenericError(now, message, error, e);

			isPersistenceOk = false;
			errorCause = getErrorMessage(error, message, now);
			causedBy = e;
        }

		if (!isPersistenceOk) {
			String finalErrorCause = errorCause;
			logger.log(Level.SEVERE, () -> finalErrorCause);
			throw new AppException(errorCause, causedBy);
		}
    }

	private Fdr getFdr(FdrMessage fdrMessage){
		Fdr fdr = new Fdr();
		fdr.setFdr(fdrMessage.getFdr());
		fdr.setPspId(fdrMessage.getPspId());
		fdr.setOrganizationId(fdrMessage.getOrganizationId());
		fdr.setRetry(fdrMessage.getRetry());
		fdr.setRevision(fdrMessage.getRevision());
		return fdr;
	}

	@FunctionalInterface
	public interface SupplierWithApiException<T> {
		T get() throws ApiException;
	}
	private static String getErrorMessage(ErrorEnum errorEnum, String message, Instant now){
		return "[ALERT][FdrJsonToXml][error="+errorEnum.name()+"] [message="+message+"] Http error at "+ now;
	}
	private static<T> void manageHttpError(String message, SupplierWithApiException<T> fn){
		try {
			fn.get();
		} catch (ApiException e) {
			String errorResponse = e.getResponseBody();
			Instant now = Instant.now();
			ErrorEnum error = ErrorEnum.HTTP_ERROR;
			String errorMessage = getErrorMessage(error, message, now);
			sendHttpError(now, message, error, errorResponse, e);
			throw new AppException(errorMessage, e);
		}
	}
	private static void sendGenericError(Instant now, String message, ErrorEnum errorEnum, Exception e){
		_sendToErrorTable(now, message, errorEnum,   Optional.empty(), e);
	}
	private static void sendHttpError(Instant now, String message, ErrorEnum errorEnum, String httpErrorResposne, Exception e){
		_sendToErrorTable(now, message, errorEnum, Optional.ofNullable(httpErrorResposne), e);
	}

	private static void _sendToErrorTable(Instant now, String message, ErrorEnum errorEnum, Optional<String> httpErrorResponse, Exception e){
		String id = UUID.randomUUID().toString();
		Map<String,Object> errorMap = new LinkedHashMap<>();
		errorMap.put(AppConstant.columnFieldId, id);
		errorMap.put(AppConstant.columnFieldCreated, now);
		errorMap.put(AppConstant.columnFieldMessage, message);
		errorMap.put(AppConstant.columnFieldErrorType, errorEnum.name());
		httpErrorResponse.ifPresent(a -> errorMap.put(AppConstant.columnFieldHttpErrorResposne, a));
		errorMap.put(AppConstant.columnFieldStackTrace, ExceptionUtils.getStackTrace(e));

		String partitionKey =  now.toString().substring(0,10);

		TableClient tableClient = getTableServiceClient().getTableClient(tableName);
		TableEntity entity = new TableEntity(partitionKey, id);
		entity.setProperties(errorMap);
		tableClient.createEntity(entity);
	}
}

