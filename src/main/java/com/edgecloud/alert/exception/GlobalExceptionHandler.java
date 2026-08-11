package com.edgecloud.alert.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MaintenanceWindowNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String,Object> handleMaintenanceNotFound(MaintenanceWindowNotFoundException ex){return error(404,"Not Found",ex.getMessage());}

    @ExceptionHandler(MaintenanceWindowValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,Object> handleMaintenanceValidation(MaintenanceWindowValidationException ex){return error(400,"Bad Request",ex.getMessage());}
    @ExceptionHandler(EscalationPolicyValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,Object> handleEscalationValidation(EscalationPolicyValidationException ex){return error(400,"Bad Request",ex.getMessage());}

    @ExceptionHandler(AlertNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleAlertNotFound(AlertNotFoundException ex) {
        return Map.of(
                "timestamp", LocalDateTime.now(),
                "status", 404,
                "error", "Not Found",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler(AlertRuleValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleRuleValidation(AlertRuleValidationException ex) {
        return error(400, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleBeanValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return error(400, "Bad Request", message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleMalformedRequest(Exception ex) {
        return error(400, "Bad Request", "Malformed request");
    }

    @ExceptionHandler(ProjectAssociationValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleAssociationValidation(ProjectAssociationValidationException ex) {
        return error(400, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleProjectNotFound(ProjectNotFoundException ex) {
        return error(404, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(ProjectAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleProjectAccessDenied(ProjectAccessDeniedException ex) {
        return error(403, "Forbidden", "Access denied");
    }

    @ExceptionHandler(ProjectServiceUnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> handleProjectServiceUnauthorized() {
        return error(401, "Unauthorized", "Authentication required");
    }

    @ExceptionHandler(AlertEvaluationValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleEvaluationValidation(AlertEvaluationValidationException ex) {
        return error(400, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(AlertEventValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleAlertEventValidation(AlertEventValidationException ex) {
        return error(400, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler({AlertOwnershipConflictException.class, InvalidAlertLifecycleTransitionException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleAlertOwnershipConflict(RuntimeException ex) {
        return error(409, "Conflict", ex.getMessage());
    }

    @ExceptionHandler(AlertOwnershipReleaseForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleAlertOwnershipReleaseForbidden() {
        return error(403, "Forbidden", "Access denied");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleUnexpectedException() {
        return error(500, "Internal Server Error", "Unexpected server error");
    }

    private Map<String, Object> error(int status, String category, String message) {
        return Map.of(
                "timestamp", LocalDateTime.now(),
                "status", status,
                "error", category,
                "message", message
        );
    }
}
