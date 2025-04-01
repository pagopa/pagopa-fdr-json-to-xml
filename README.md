# pagoPA Functions fdr-json-to-xml-fn

Java fdr-json-to-xml Azure Function.
The function aims to convert FdR reports from JSON format to XML format.

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=pagopa_pagopa-fdr-re-to-datastore&metric=alert_status)](https://sonarcloud.io/dashboard?id=pagopa_pagopa-fdr-re-to-datastore)


---

## Run locally with Docker
`docker build -t pagopa-functions-fdr-json-to-xml .`

`docker run -it -rm -p 8999:80 pagopa-functions-fdr-json-to-xml`

### Test
`curl http://localhost:8999/example`

### Configuration

`cp .env.example .env`

Fill with the environment variables

```
set -o allexport                                                                 
source .env     
set +o allexport
```

## Run locally with Maven

In order to autogenerate the required classes, please run the command:  
`mvn clean package`

In order to test the Azure Function in local environment, please run the command:
`mvn azure-functions:run`

`mvn -f pom.xml clean package && mvn -e azure-functions:run`

### Test
`curl http://localhost:7071/example`

---
