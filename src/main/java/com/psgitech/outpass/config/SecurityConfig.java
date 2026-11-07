package com.psgitech.outpass.config;

import com.psgitech.outpass.repo.Repos;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public UserDetailsService userDetailsService(Repos.Users users) {
        return email -> users.findByEmailIgnoreCase(email)
                .map(u -> User.withUsername(u.getEmail())
                        .password(u.getPasswordHash())
                        .roles(u.getRole().name())
                        .disabled(!u.isEnabled() || !u.isLocalLogin())      // Google-only accounts cannot use the password form
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }

    @Bean
    public AuthenticationSuccessHandler successHandler() {
        return (request, response, authentication) -> {
            String target = "/";
            for (GrantedAuthority a : authentication.getAuthorities()) {
                target = switch (a.getAuthority()) {
                    case "ROLE_STUDENT" -> "/student";
                    case "ROLE_TUTOR" -> "/tutor";
                    case "ROLE_HOD" -> "/hod";
                    case "ROLE_HEAD" -> "/head";
                    case "ROLE_SECURITY" -> "/gate";
                    case "ROLE_ADMIN" -> "/admin";
                    default -> target;
                };
            }
            response.sendRedirect(request.getContextPath() + target);
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationSuccessHandler successHandler,
                                           ObjectProvider<ClientRegistrationRepository> registrations,
                                           AppOidcUserService oidcUserService, OutpassProperties props) throws Exception {
        http
            .authorizeHttpRequests(a -> a
                .requestMatchers("/login", "/demo", "/demo/**", "/oauth2/**", "/login/oauth2/**", "/error", "/css/**", "/js/**", "/img/**", "/icons/**",
                        "/manifest.webmanifest", "/sw.js", "/offline.html").permitAll()
                .requestMatchers("/student/**").hasRole("STUDENT")
                .requestMatchers("/tutor/**").hasRole("TUTOR")
                .requestMatchers("/hod/**").hasRole("HOD")
                .requestMatchers("/head/**").hasRole("HEAD")
                .requestMatchers("/gate/**").hasRole("SECURITY")
                .requestMatchers("/admin/**", "/export/template/**").hasRole("ADMIN")
                .requestMatchers("/export/**").hasAnyRole("ADMIN", "HEAD", "HOD")
                .anyRequest().authenticated())
            .formLogin(f -> f.loginPage("/login").successHandler(successHandler)
                    .failureUrl("/login?error").permitAll())
            .logout(l -> l.logoutSuccessUrl("/login?logout"))
            .headers(h -> h.frameOptions(fo -> fo.sameOrigin()));
        ClientRegistrationRepository repo = registrations.getIfAvailable();
        if (repo != null) {
            String domain = props.getGoogleDomain() == null ? "" : props.getGoogleDomain().trim();
            var resolver = new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
            resolver.setAuthorizationRequestCustomizer(c -> c.additionalParameters(p -> {
                if (!domain.isEmpty()) p.put("hd", domain);          // hint: show only college accounts
                p.put("prompt", "select_account");
            }));
            http.oauth2Login(o -> o
                    .loginPage("/login")
                    .authorizationEndpoint(a -> a.authorizationRequestResolver(resolver))
                    .userInfoEndpoint(u -> u.oidcUserService(oidcUserService))
                    .successHandler(successHandler)
                    .failureHandler((req, res, ex) -> {
                        String code = ex instanceof OAuth2AuthenticationException oe ? oe.getError().getErrorCode() : "google_failed";
                        res.sendRedirect(req.getContextPath() + "/login?google=" + code);
                    }));
        }
        return http.build();
    }
}
