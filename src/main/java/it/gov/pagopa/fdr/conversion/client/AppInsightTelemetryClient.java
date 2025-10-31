package it.gov.pagopa.fdr.conversion.client;

import com.microsoft.applicationinsights.TelemetryClient;
import com.microsoft.applicationinsights.TelemetryConfiguration;
import java.util.Map;

/** Azure Application Insight Telemetry client */
public class AppInsightTelemetryClient {

  private final String connectionString = System.getenv("APPLICATIONINSIGHTS_CONNECTION_STRING");

  private final TelemetryClient telemetryClient;

  public AppInsightTelemetryClient() {
    TelemetryConfiguration aDefault = TelemetryConfiguration.createDefault();
    aDefault.setConnectionString(connectionString);
    this.telemetryClient = new TelemetryClient(aDefault);
  }

  AppInsightTelemetryClient(TelemetryClient telemetryClient) {
    this.telemetryClient = telemetryClient;
  }

  /**
   * Create a custom event on Application Insight with the provided information
   *
   * @param details details of the custom event
   * @param e exception added to the custom event
   */
  public void createCustomEventForAlert(String details, Object e) {
    Map<String, String> props;
    if (e instanceof Exception) {
      Exception ex = (Exception) e;
      props =
              Map.of(
                      "type",
                      "FDR_JSON_TO_XML_ERROR",
                      "title",
                      "FdrJsonToXml last retry",
                      "details",
                      details,
                      "cause",
                      ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
    } else {
      props =
              Map.of(
                      "type",
                      "FDR_JSON_TO_XML_ERROR",
                      "title",
                      "FdrJsonToXml last retry",
                      "details",
                      details,
                      "cause",
                      "Out-of-Memory");
    }
    this.telemetryClient.trackEvent("FDR_JSON_TO_XML_ALERT", props, null);
  }
}
