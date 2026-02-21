package com.knapp.kisoft.mock.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "knapp.mock")
public class KnappMockProperties {

    /**
     * Als true: authent removes requests zonder valid OAuth2 token (voor lokaal testen).
     * In productie altijd false.
     */
    private boolean bypassAuth = false;

    public boolean isBypassAuth() {
        return bypassAuth;
    }

    public void setBypassAuth(boolean bypassAuth) {
        this.bypassAuth = bypassAuth;
    }
}
