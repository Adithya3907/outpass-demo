package com.psgitech.outpass.web;

import com.psgitech.outpass.config.OutpassProperties;
import com.psgitech.outpass.repo.Repos;
import com.psgitech.outpass.service.BusinessException;
import com.psgitech.outpass.service.ExpiryService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {
    private final Repos.Users users;
    private final Repos.Notifications notifications;
    private final OutpassProperties props;
    private final ExpiryService expiry;
    private final org.springframework.beans.factory.ObjectProvider<com.psgitech.outpass.demo.DemoResetService> demoReset;

    @ModelAttribute
    public void common(Model m, Principal principal) {
        m.addAttribute("collegeName", props.getCollegeName());
        m.addAttribute("demoMode", props.isDemoMode());
        var dr = demoReset.getIfAvailable();
        if (dr != null) { m.addAttribute("demoNextReset", dr.getNextResetMillis()); m.addAttribute("demoMinutes", props.getDemoResetMinutes()); }
        if (principal != null) {
            expiry.runThrottled();
            users.findByEmailIgnoreCase(principal.getName()).ifPresent(u -> {
                m.addAttribute("me", u);
                m.addAttribute("unread", notifications.countByUserAndSeenFalse(u));
            });
        }
    }

    @ExceptionHandler(BusinessException.class)
    public String business(BusinessException e, Model m, Principal principal, HttpServletResponse resp) {
        resp.setStatus(400);
        common(m, principal);
        m.addAttribute("title", "Request not allowed");
        m.addAttribute("message", e.getMessage());
        return "error";
    }
}
