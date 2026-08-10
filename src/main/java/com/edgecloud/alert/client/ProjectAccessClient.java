package com.edgecloud.alert.client;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.edgecloud.alert.exception.ProjectAccessDeniedException;
import com.edgecloud.alert.exception.ProjectNotFoundException;
import com.edgecloud.alert.exception.ProjectServiceUnauthorizedException;

@Component
public class ProjectAccessClient {

    private final RestClient restClient;

    public ProjectAccessClient(RestClient.Builder builder,
                               @Value("${edgecloud.downstream.project-base-url:http://localhost:8086}") String projectBaseUrl) {
        this.restClient = builder.baseUrl(projectBaseUrl).build();
    }

    public ProjectWorkspaceResponse getWorkspace(UUID projectId, String bearerToken) {
        return restClient.get()
                .uri("/api/v2/projects/{projectId}/workspace", projectId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .retrieve()
                .onStatus(status -> status.value() == 401, (request, response) -> {
                    throw new ProjectServiceUnauthorizedException();
                })
                .onStatus(status -> status.value() == 403, (request, response) -> {
                    throw new ProjectAccessDeniedException("Access denied");
                })
                .onStatus(status -> status.value() == 404, (request, response) -> {
                    throw new ProjectNotFoundException("Project not found");
                })
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new ProjectAccessDeniedException("Project access validation failed");
                })
                .body(ProjectWorkspaceResponse.class);
    }
}