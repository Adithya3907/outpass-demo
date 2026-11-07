package com.psgitech.outpass.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/** Registers "Sign in with Google" only when GOOGLE_CLIENT_ID is set. */
@Configuration
@ConditionalOnExpression("!'${outpass.google-client-id:}'.isEmpty()")
public class GoogleLoginConfig {
    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(OutpassProperties props) {
        ClientRegistration google = CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(props.getGoogleClientId())
                .clientSecret(props.getGoogleClientSecret())
                .scope("openid", "profile", "email")
                .build();
        return new InMemoryClientRegistrationRepository(google);
    }
}
