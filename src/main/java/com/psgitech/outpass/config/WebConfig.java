package com.psgitech.outpass.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Open-Session-In-View is kept for normal pages (the templates lazy-load associations),
 * but it is NOT applied to the long-lived /events SSE stream or to static files.
 *
 * Why: Spring's Hibernate adapter uses connection handling DELAYED_ACQUISITION_AND_HOLD, i.e. once a
 * Session runs its first query it keeps its JDBC connection until the Session is closed. With the
 * default OSIV the Session of an async request (SSE) stays open until the stream ends - up to 30
 * minutes - so every open/abandoned tab pinned one pool connection until the pool (10) ran dry.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private AccountGateInterceptor accountGate;
    @Autowired
    private org.springframework.beans.factory.ObjectProvider<com.psgitech.outpass.demo.DemoGuardInterceptor> demoGuard;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        OpenEntityManagerInViewInterceptor osiv = new OpenEntityManagerInViewInterceptor();
        osiv.setEntityManagerFactory(entityManagerFactory);
        registry.addWebRequestInterceptor(osiv)
                .excludePathPatterns("/events", "/css/**", "/js/**", "/img/**", "/icons/**", "/sw.js", "/manifest.webmanifest");
        var guard = demoGuard.getIfAvailable();
        if (guard != null) registry.addInterceptor(guard).addPathPatterns("/admin/**", "/account/**");
        registry.addInterceptor(accountGate)
                .excludePathPatterns("/account/**", "/login", "/logout", "/error", "/events", "/css/**", "/js/**", "/img/**",
                        "/icons/**", "/sw.js", "/manifest.webmanifest", "/offline.html", "/photos/**");
    }
}
