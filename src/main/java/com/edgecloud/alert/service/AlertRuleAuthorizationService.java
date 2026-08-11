package com.edgecloud.alert.service;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.edgecloud.alert.client.ProjectAccessClient;
import com.edgecloud.alert.client.ProjectWorkspaceResponse;
import com.edgecloud.alert.dto.AlertRuleRequest;
import com.edgecloud.alert.exception.ProjectAccessDeniedException;
import com.edgecloud.alert.exception.ProjectAssociationValidationException;
import com.edgecloud.alert.security.EdgeCloudJwtAuthenticationToken;

@Service
public class AlertRuleAuthorizationService {

    private final ProjectAccessClient projectAccessClient;

    public AlertRuleAuthorizationService(ProjectAccessClient projectAccessClient) {
        this.projectAccessClient = projectAccessClient;
    }

    public void requireRead(UUID projectId, Authentication authentication) {
        ProjectWorkspaceResponse workspace = workspace(projectId, authentication);
        if (!isAdmin(authentication) && !isReadRole(workspace.callerProjectRole())) {
            throw new ProjectAccessDeniedException("Access denied");
        }
    }

    public void requireMutation(UUID projectId, Authentication authentication, AlertRuleRequest request) {
        ProjectWorkspaceResponse workspace = workspace(projectId, authentication);
        if (!isAdmin(authentication) && !isMutationRole(workspace.callerProjectRole())) {
            throw new ProjectAccessDeniedException("Access denied");
        }
        validateAssociations(workspace, request);
    }

    public void requireMutation(UUID projectId, Authentication authentication) {
        requireMutation(projectId, authentication, null);
    }

    public void requireAdminMutation(UUID projectId, Authentication authentication) {
        ProjectWorkspaceResponse workspace = workspace(projectId, authentication);
        if (!isAdmin(authentication) && !"PROJECT_ADMIN".equals(workspace.callerProjectRole())) {
            throw new ProjectAccessDeniedException("Project administrator access required");
        }
    }

    private ProjectWorkspaceResponse workspace(UUID projectId, Authentication authentication) {
        if (!(authentication instanceof EdgeCloudJwtAuthenticationToken token)) {
            throw new ProjectAccessDeniedException("Access denied");
        }
        return projectAccessClient.getWorkspace(projectId, token.getToken());
    }

    private boolean isReadRole(String role) {
        return "PROJECT_ADMIN".equals(role) || "OPERATOR".equals(role) || "VIEWER".equals(role);
    }

    private boolean isMutationRole(String role) {
        return "PROJECT_ADMIN".equals(role) || "OPERATOR".equals(role);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication instanceof EdgeCloudJwtAuthenticationToken token
                && "ADMIN".equals(token.getPlatformRole());
    }

    private void validateAssociations(ProjectWorkspaceResponse workspace, AlertRuleRequest request) {
        if (request == null) {
            return;
        }
        if (request.deviceId() != null && request.serviceId() != null) {
            throw new ProjectAssociationValidationException("deviceId and serviceId cannot both be set");
        }
        if (request.deviceId() != null && !workspace.deviceIds().contains(request.deviceId())) {
            throw new ProjectAssociationValidationException("Device is not associated with the project");
        }
        if (request.serviceId() != null && !workspace.serviceIds().contains(request.serviceId())) {
            throw new ProjectAssociationValidationException("Service is not associated with the project");
        }
    }
}
