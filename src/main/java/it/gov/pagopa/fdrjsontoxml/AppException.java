package it.gov.pagopa.fdrjsontoxml;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
  public AppException(String message, Throwable cause) {
    super(message, cause);
  }
}
