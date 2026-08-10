package com.edgecloud.alert.exception;

public class ProjectServiceUnauthorizedException extends RuntimeException {

    public ProjectServiceUnauthorizedException() {
        super("Project access validation failed");
    }
}