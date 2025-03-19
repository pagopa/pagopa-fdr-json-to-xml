package it.gov.pagopa.fdr.conversion.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
  public AppException(String message, Throwable cause) {
    super(message, cause);
  }
}
