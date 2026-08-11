package com.edgecloud.alert.client;

public class NotificationLifecycleClientException extends RuntimeException {
    private final boolean retryable;
    private final String category;

    public NotificationLifecycleClientException(boolean retryable, String category, String message, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.category = category;
    }

    public boolean isRetryable() { return retryable; }
    public String getCategory() { return category; }
}
