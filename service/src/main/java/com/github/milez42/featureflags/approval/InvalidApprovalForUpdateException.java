package com.github.milez42.featureflags.approval;

public class InvalidApprovalForUpdateException extends RuntimeException {
  public InvalidApprovalForUpdateException(String message) {
    super(message);
  }
}
