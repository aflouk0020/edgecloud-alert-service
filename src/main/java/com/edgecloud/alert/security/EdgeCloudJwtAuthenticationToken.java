package com.edgecloud.alert.security;

import java.util.List;
import java.util.UUID;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class EdgeCloudJwtAuthenticationToken extends AbstractAuthenticationToken {

    private final UUID userId;
    private final String platformRole;
    private final String token;

    public EdgeCloudJwtAuthenticationToken(UUID userId, String platformRole, String token) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + platformRole)));
        this.userId = userId;
        this.platformRole = platformRole;
        this.token = token;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() { return token; }

    @Override
    public Object getPrincipal() { return userId; }

    public UUID getUserId() { return userId; }

    public String getPlatformRole() { return platformRole; }

    public String getToken() { return token; }
}