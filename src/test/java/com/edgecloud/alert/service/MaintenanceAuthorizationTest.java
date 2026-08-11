package com.edgecloud.alert.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.edgecloud.alert.client.ProjectAccessClient;
import com.edgecloud.alert.client.ProjectWorkspaceResponse;
import com.edgecloud.alert.exception.ProjectAccessDeniedException;
import com.edgecloud.alert.exception.ProjectAssociationValidationException;
import com.edgecloud.alert.security.EdgeCloudJwtAuthenticationToken;

class MaintenanceAuthorizationTest {

    private final ProjectAccessClient client = mock(ProjectAccessClient.class);
    private final AlertRuleAuthorizationService service = new AlertRuleAuthorizationService(client);
    private final UUID projectId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID serviceId = UUID.randomUUID();

    @Test
    void viewerAndOperatorCannotMutateMaintenanceWindows() {
        for (String role : List.of("VIEWER", "OPERATOR")) {
            var token = token(role);
            when(client.getWorkspace(projectId, "jwt")).thenReturn(workspace(role));

            assertThatThrownBy(() -> service.requireMaintenanceMutation(projectId, token, null, null))
                    .isInstanceOf(ProjectAccessDeniedException.class);
        }
    }

    @Test
    void projectAndPlatformAdminsCanMutateMaintenanceWindows() {
        when(client.getWorkspace(projectId, "jwt")).thenReturn(workspace("PROJECT_ADMIN"));
        assertThat(service.requireMaintenanceMutation(projectId, token("PROJECT_ADMIN"), serviceId, null))
                .isEqualTo(actorId);

        when(client.getWorkspace(projectId, "jwt")).thenReturn(workspace("VIEWER"));
        assertThatCode(() -> service.requireMaintenanceMutation(projectId, token("ADMIN"), null, "device-1"))
                .doesNotThrowAnyException();
    }

    @Test
    void targetsMustBelongToTheProject() {
        when(client.getWorkspace(projectId, "jwt")).thenReturn(workspace("PROJECT_ADMIN"));

        assertThatThrownBy(() -> service.requireMaintenanceMutation(
                projectId, token("PROJECT_ADMIN"), UUID.randomUUID(), null))
                .isInstanceOf(ProjectAssociationValidationException.class);
        assertThatThrownBy(() -> service.requireMaintenanceMutation(
                projectId, token("PROJECT_ADMIN"), null, "other-device"))
                .isInstanceOf(ProjectAssociationValidationException.class);
    }

    private EdgeCloudJwtAuthenticationToken token(String role) {
        return new EdgeCloudJwtAuthenticationToken(actorId, role, "jwt");
    }

    private ProjectWorkspaceResponse workspace(String role) {
        return new ProjectWorkspaceResponse(projectId, "Project", "ACTIVE", actorId, role,
                List.of(serviceId), List.of("device-1"));
    }
}
