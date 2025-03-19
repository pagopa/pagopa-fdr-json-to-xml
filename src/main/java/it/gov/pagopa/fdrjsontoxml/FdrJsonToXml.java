package it.gov.pagopa.fdrjsontoxml;

import com.azure.data.tables.TableClient;
import com.azure.data.tables.TableServiceClient;
import com.azure.data.tables.TableServiceClientBuilder;
import com.azure.data.tables.models.TableEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.*;
import it.gov.pagopa.fdrjsontoxml.entity.FdrMessage;
import it.gov.pagopa.fdrjsontoxml.exception.AppException;
import it.gov.pagopa.fdrjsontoxml.exception.ErrorEnum;
import it.gov.pagopa.fdrjsontoxml.util.AppConstant;
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

	/*
	Executing this Azure Function in exponential retry, with steps:
	- retry 0: 0
	- retry 1: 10s
	- retry 2: 20s
	- retry 3: 40s
	- ...
	 */
	@FunctionName("BlobFdrJsonToXmlEventProcessor")
	@ExponentialBackoffRetry(maxRetryCount = 10, maximumInterval = "01:30:00", minimumInterval = "00:00:10")
	public void sendJsonToBeProcessed (
			@BlobTrigger(
					name = "jsonTrigger",
					connection = "STORAGE_ACCOUNT_CONN_STRING",
					path = "jsonsharefile/{fileName}",
					dataType = "binary") byte[] content,
			@BindingName("fileName") String fileName,
			final ExecutionContext context) {
	}
}

