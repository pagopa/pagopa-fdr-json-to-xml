package it.gov.pagopa.fdrjsontoxml.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
  public AppException(String message, Throwable cause) {
    super(message, cause);
  }
}
