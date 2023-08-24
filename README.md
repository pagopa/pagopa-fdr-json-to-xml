# pagoPA Functions fdr-json-to-xml-fn

Java fdr-json-to-xml Azure Function.
The function aims to dump RE sent via Azure Event Hub to a CosmosDB, with a TTL of 120 days, and to an Azure Table Storage with a TTL of 10 years.

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=pagopa_pagopa-fdr-re-to-datastore&metric=alert_status)](https://sonarcloud.io/dashboard?id=pagopa_pagopa-fdr-re-to-datastore)


---

## Run locally with Docker
`docker build -t pagopa-functions-fdr-json-to-xml .`

`docker run -it -rm -p 8999:80 pagopa-functions-fdr-json-to-xml`

### Test
`curl http://localhost:8999/example`

## Run locally with Maven

`mvn clean package`

`mvn azure-functions:run`

### Test
`curl http://localhost:7071/example`

---
