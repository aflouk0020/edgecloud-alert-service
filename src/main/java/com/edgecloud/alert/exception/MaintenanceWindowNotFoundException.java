package com.edgecloud.alert.exception;

public class MaintenanceWindowNotFoundException extends RuntimeException {
    public MaintenanceWindowNotFoundException(String message) {
        super(message);
    }
}
