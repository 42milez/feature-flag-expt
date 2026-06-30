package com.github.milez42.featureflags.approval;

import com.github.milez42.featureflags.error.HttpException;
import org.springframework.http.HttpStatus;

public class ApprovalWorkflowException extends HttpException {
  private final HttpStatus status;
  private final String title;

  public static ApprovalWorkflowException notFound() {
    return new ApprovalWorkflowException(
        HttpStatus.NOT_FOUND, "Approval request not found", "Approval request not found");
  }

  public static ApprovalWorkflowException forbidden(String message) {
    return new ApprovalWorkflowException(HttpStatus.FORBIDDEN, "Approval forbidden", message);
  }

  public static ApprovalWorkflowException conflict(String message) {
    return new ApprovalWorkflowException(HttpStatus.CONFLICT, "Approval conflict", message);
  }

  private ApprovalWorkflowException(HttpStatus status, String title, String message) {
    super(message);
    this.status = status;
    this.title = title;
  }

  @Override
  public HttpStatus status() {
    return status;
  }

  @Override
  public String title() {
    return title;
  }
}
