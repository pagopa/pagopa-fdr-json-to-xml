package it.gov.pagopa.fdrjsontoxml;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.time.Instant;
import java.util.Iterator;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Azure Functions with Azure Http trigger.
 */
public class FdrJsonError {


	private static final String tableStorageConnString = System.getenv("TABLE_STORAGE_CONN_STRING");
	private static final String tableName = System.getenv("TABLE_STORAGE_TABLE_NAME");

	private static final String queueConnString = System.getenv("STORAGE_ACCOUNT_CONN_STRING");
	private static final String queueName = System.getenv("QUEUE_NAME");
	private static TableServiceClient tableServiceClient = null;
	private static QueueClient queueClient;

	private static TableServiceClient getTableServiceClient(){
		if(tableServiceClient==null){
			tableServiceClient = new TableServiceClientBuilder()
					.connectionString(tableStorageConnString)
					.buildClient();
			tableServiceClient.createTableIfNotExists(tableName);
		}
		return tableServiceClient;
	}


	private static QueueClient getQueueClient(){
		if(queueClient==null){
			QueueClient q = new QueueClientBuilder().connectionString(queueConnString).queueName(queueName).buildClient();
			q.createIfNotExists();
			queueClient = q;
		}
		return queueClient;
	}
	@FunctionName("JsonErrorRetry")
	public HttpResponseMessage run (
			@HttpTrigger(name = "JsonErrorRetryTrigger",
			methods = {HttpMethod.GET},
			route = "jsonerror",
			authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
			final ExecutionContext context) {

		Logger logger = context.getLogger();

		try{
			String partitionKey = request.getQueryParameters().get("partitionKey");
			String rowKey = request.getQueryParameters().get("rowKey");
			TableClient tableClient = getTableServiceClient().getTableClient(tableName);
			String message;
			if(partitionKey != null && rowKey != null){
				message = "Retrieve a single entity with [partitionKey="+partitionKey+"] [rowKey="+rowKey+"]";
				logger.info(message);
				TableEntity tableEntity = tableClient.getEntity(partitionKey, rowKey);
				process(logger, tableClient, tableEntity);
			} else {
				message = "Retrieve all entity";
				logger.info(message);
				Iterator<TableEntity> itr = tableClient.listEntities().iterator();
				while (itr.hasNext()) {
					TableEntity tableEntity = itr.next();
					process(logger, tableClient, tableEntity);
				}
			}
			logger.info("Done processing events");
			return request
					.createResponseBuilder(HttpStatus.OK)
					.body(" SUCCESS. "+message)
					.build();
		} catch (Exception e) {
			logger.log(Level.SEVERE, "[ALERT] Generic error at "+Instant.now(), e);
			return request
					.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("FAILED. "+ExceptionUtils.getStackTrace(e))
					.build();
		}
	}


	private static void process(Logger logger, TableClient tableClient, TableEntity tableEntity) throws JsonProcessingException {

		//riaccodo per far partire il trigger con il retry +1 in modo da far capire a
		//fdr fase 1 che ci sto riprovando e potrebbe dover fare update
		String message = (String)tableEntity.getProperty(AppConstant.columnFieldMessage);
		ObjectMapper objectMapper = new ObjectMapper();
		FdrMessage fdrMessage = objectMapper.readValue(message, FdrMessage.class);
		fdrMessage.setRetry(fdrMessage.getRetry()+1);
		getQueueClient().sendMessage(objectMapper.writeValueAsString(fdrMessage));

		//cancello la riga degli errori
		logger.info("Delete row from error table");
		tableClient.deleteEntity(tableEntity);
	}


}
