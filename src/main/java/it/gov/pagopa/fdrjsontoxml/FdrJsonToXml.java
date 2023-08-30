package it.gov.pagopa.fdrjsontoxml;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.QueueTrigger;
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
    public void processNodoReEvent (
			@QueueTrigger(
					name = "jsonTrigger",
					queueName = "flowidsendqueue",
					connection = "STORAGE_ACCOUNT_CONN_STRING")
			String b64Message,
			final ExecutionContext context) {

		Logger logger = context.getLogger();
		String message = new String(Base64.getDecoder().decode(b64Message), StandardCharsets.UTF_8);
		logger.info("Queue message: " + message);

		try {
			// read json
			ObjectMapper objectMapper = new ObjectMapper();
			FdrMessage fdrMessage = objectMapper.readValue(message, FdrMessage.class);
			logger.info("Process fdr=["+fdrMessage.getFdr()+"], pspId=["+fdrMessage.getPspId()+"]");

			// create body for notify FDR
			Fdr fdr = getFdr(fdrMessage);
			// call notify FDR
			logger.info("Calling... notify");
			manageHttpError(logger, message, () ->
					getFdrFase1Api().notifyFdrToConvert(fdr)
			);

			logger.info("Done processing events");
		} catch (AppException e) {
			logger.info("Failure processing events");
		} catch (Exception e) {
			Instant now = Instant.now();
			ErrorEnum error = ErrorEnum.GENERIC_ERROR;
			logger.log(Level.SEVERE, getErrorMessage(error, message, now), e);
			sendGenericError(logger, now, message, error, e);
			logger.info("Failure processing events");
        }
    }

	private Fdr getFdr(FdrMessage fdrMessage){
		Fdr fdr = new Fdr();
		fdr.setFdr(fdrMessage.getFdr());
		fdr.setPspId(fdrMessage.getPspId());
		fdr.setRetry(fdrMessage.getRetry());
		fdr.setRevision(fdrMessage.getRevision());
		return fdr;
	}

	@FunctionalInterface
	public interface SupplierWithApiException<T> {
		T get() throws ApiException;
	}
	private static String getErrorMessage(ErrorEnum errorEnum, String message, Instant now){
		return "[ALERT] [error="+errorEnum.name()+"] [message="+message+"] Http error at "+ now;
	}
	private static<T> void manageHttpError(Logger logger, String message, SupplierWithApiException<T> fn){
		try {
			fn.get();
		} catch (ApiException e) {
			String errorResposne = e.getResponseBody();

			Instant now = Instant.now();
			ErrorEnum error = ErrorEnum.HTTP_ERROR;
			logger.log(Level.SEVERE, getErrorMessage(error, message, now), e);
			sendHttpError(logger, now, message, error, errorResposne, e);
			throw new AppException(message, e);
		}
	}
	private static void sendGenericError(Logger logger, Instant now, String message, ErrorEnum errorEnum, Exception e){
		_sendToErrorTable(logger, now, message, errorEnum,   Optional.empty(), e);
	}
	private static void sendHttpError(Logger logger, Instant now, String message, ErrorEnum errorEnum, String httpErrorResposne, Exception e){
		_sendToErrorTable(logger, now, message, errorEnum, Optional.ofNullable(httpErrorResposne), e);
	}

	private static void _sendToErrorTable(Logger logger, Instant now, String message, ErrorEnum errorEnum, Optional<String> httpErrorResposne, Exception e){
		String id = UUID.randomUUID().toString();
		Map<String,Object> errorMap = new LinkedHashMap<>();
		errorMap.put(AppConstant.columnFieldId, id);
		errorMap.put(AppConstant.columnFieldCreated, now);
		errorMap.put(AppConstant.columnFieldMessage, message);
		errorMap.put(AppConstant.columnFieldErrorType, errorEnum.name());
		httpErrorResposne.ifPresent(a -> errorMap.put(AppConstant.columnFieldHttpErrorResposne, a));
		errorMap.put(AppConstant.columnFieldStackTrace, ExceptionUtils.getStackTrace(e));

		String partitionKey =  now.toString().substring(0,10);

		logger.info("Send to "+tableName+" record with "+AppConstant.columnFieldId+"="+id);
		TableClient tableClient = getTableServiceClient().getTableClient(tableName);
		TableEntity entity = new TableEntity(partitionKey, id);
		entity.setProperties(errorMap);
		tableClient.createEntity(entity);
	}
}

