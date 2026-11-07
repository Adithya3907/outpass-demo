package com.psgitech.outpass.config;

import com.psgitech.outpass.domain.UserAccount;
import com.psgitech.outpass.repo.Repos;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * After Google has proven who the person is, we only let in people the college has registered:
 * the verified Google e-mail must exist in our database, be enabled, and belong to the college domain.
 * The person's role (and therefore the portal they see) always comes from our database, never from Google.
 */
@Component
@RequiredArgsConstructor
public class AppOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final Repos.Users users;
    private final OutpassProperties props;
    private final OidcUserService delegate = new OidcUserService();

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser google = delegate.loadUser(request);
        String email = google.getEmail();
        if (email == null || !Boolean.TRUE.equals(google.getEmailVerified()))
            throw fail("google_failed", "Google did not return a verified e-mail address.");
        email = email.trim().toLowerCase(Locale.ROOT);

        String domain = props.getGoogleDomain() == null ? "" : props.getGoogleDomain().trim().toLowerCase(Locale.ROOT);
        if (!domain.isEmpty() && !email.endsWith("@" + domain))
            throw fail("google_domain", "Please sign in with your @" + domain + " account.");

        UserAccount u = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> fail("google_not_registered", "This e-mail is not registered in the outpass system."));
        if (!u.isEnabled()) throw fail("google_disabled", "This account is disabled.");

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()));
        // principal name = e-mail, exactly like password sign-in, so the whole application works unchanged
        return new DefaultOidcUser(authorities, google.getIdToken(), google.getUserInfo(), "email");
    }

    private static OAuth2AuthenticationException fail(String code, String message) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, message, null), message);
    }
}
