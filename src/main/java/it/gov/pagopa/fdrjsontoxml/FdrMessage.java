package it.gov.pagopa.fdrjsontoxml;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
public class FdrMessage {

  private String fdr;

  private String pspId;

  private String organizationId;

  private Long retry;

  private Long revision;
}
