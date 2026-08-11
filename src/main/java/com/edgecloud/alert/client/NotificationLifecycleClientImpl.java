package com.edgecloud.alert.client;

import java.net.http.HttpClient;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.edgecloud.alert.config.NotificationServiceProperties;

@Component
public class NotificationLifecycleClientImpl implements NotificationLifecycleClient {
    static final String PATH = "/internal/notifications/alert-events";
    static final String KEY_HEADER = "X-Internal-Service-Key";

    private final RestClient restClient;
    private final String internalServiceKey;

    public NotificationLifecycleClientImpl(RestClient.Builder builder, NotificationServiceProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
        this.internalServiceKey = properties.internalServiceKey();
    }

    @Override
    public AlertLifecycleNotificationResponse publish(AlertLifecycleNotificationRequest request) {
        try {
            AlertLifecycleNotificationResponse response = restClient.post().uri(PATH)
                    .header(KEY_HEADER, internalServiceKey).body(request)
                    .retrieve().body(AlertLifecycleNotificationResponse.class);
            if (response == null || !request.sourceEventId().equals(response.sourceEventId())) {
                throw new NotificationLifecycleClientException(false, "INVALID_RESPONSE",
                        "Notification intake returned an invalid response", null);
            }
            return response;
        } catch (RestClientResponseException ex) {
            HttpStatusCode status = ex.getStatusCode();
            boolean retryable = status.value() == 429 || status.is5xxServerError();
            throw new NotificationLifecycleClientException(retryable, "HTTP_" + status.value(),
                    "Notification intake returned HTTP " + status.value(), ex);
        } catch (ResourceAccessException ex) {
            throw new NotificationLifecycleClientException(true, "CONNECTION", "Notification intake unavailable", ex);
        }
    }
}
