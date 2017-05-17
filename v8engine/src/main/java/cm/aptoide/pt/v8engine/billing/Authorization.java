/*
 * Copyright (c) 2017.
 * Modified by Marcelo Benites on 02/01/2017.
 */

package cm.aptoide.pt.v8engine.billing;

public abstract class Authorization {

  private final int paymentId;
  private final String payerId;
  private Status status;

  public Authorization(int paymentId, String payerId, Status status) {
    this.paymentId = paymentId;
    this.payerId = payerId;
    this.status = status;
  }

  public String getPayerId() {
    return payerId;
  }

  public int getPaymentId() {
    return paymentId;
  }

  public boolean isAuthorized() {
    return Status.ACTIVE.equals(status) || Status.NONE.equals(status);
  }

  public boolean isPending() {
    return Status.PENDING.equals(status)
        || isPendingUserConsent();
  }

  public boolean isInactive() {
    return Status.INACTIVE.equals(status);
  }

  public boolean isPendingUserConsent() {
    return Status.INITIATED.equals(status);
  }

  public boolean isFailed() {
    return Status.CANCELLED.equals(status)
        || Status.EXPIRED.equals(status)
        || Status.SESSION_EXPIRED.equals(status)
        || Status.UNKNOWN_ERROR.equals(status);
  }

  public Status getStatus() {
    return status;
  }

  public enum Status {
    NONE, INACTIVE, ACTIVE, INITIATED, PENDING, CANCELLED, EXPIRED, SESSION_EXPIRED, UNKNOWN_ERROR
  }
}
