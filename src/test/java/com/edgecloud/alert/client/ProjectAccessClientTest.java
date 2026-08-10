package com.edgecloud.alert.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ProjectAccessClientTest {

    @Test
    void propagatesBearerTokenToProjectWorkspaceRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ProjectAccessClient client = new ProjectAccessClient(builder, "http://project-service");
        UUID projectId = UUID.randomUUID();

        server.expect(requestTo("http://project-service/api/v2/projects/" + projectId + "/workspace"))
                .andExpect(header("Authorization", "Bearer token"))
                .andRespond(withSuccess("""
                        {"projectId":"%s","projectName":"Project","projectStatus":"ACTIVE","callerUserId":null,"callerProjectRole":"OPERATOR","serviceIds":["%s"],"deviceIds":["device-1"]}
                        """.formatted(projectId, UUID.randomUUID()), MediaType.APPLICATION_JSON));

        ProjectWorkspaceResponse response = client.getWorkspace(projectId, "token");

        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.callerProjectRole()).isEqualTo("OPERATOR");
        assertThat(response.deviceIds()).containsExactly("device-1");
        server.verify();
    }
}
