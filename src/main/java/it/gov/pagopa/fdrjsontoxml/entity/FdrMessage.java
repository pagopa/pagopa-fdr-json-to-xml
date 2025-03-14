package it.gov.pagopa.fdrjsontoxml.entity;

import lombok.Data;

@Data
public class FdrMessage {

  private String fdr;

  private String pspId;

  private String organizationId;

  private Long retry;

  private Long revision;
}
