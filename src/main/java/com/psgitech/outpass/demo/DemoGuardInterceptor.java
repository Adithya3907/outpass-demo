package com.psgitech.outpass.demo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.regex.Pattern;

/**
 * PUBLIC DEMO ONLY. Visitors can look at everything and use the real workflows, but the few actions that could wreck
 * the demo for the next visitor (delete people, wipe data, imports, password / sign-in / account changes) are refused.
 */
@Component
@ConditionalOnProperty(prefix = "outpass", name = "demo-mode", havingValue = "true")
public class DemoGuardInterceptor implements HandlerInterceptor {
    private static final Pattern BLOCKED = Pattern.compile(
            "^/admin/(cleanup.*|import/run|users/delete|users/import/.*|users/\\d+/(password|login-mode|toggle|student|staff|basic))$|^/account/password$");

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        if ("GET".equals(req.getMethod())) return true;
        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (!BLOCKED.matcher(path).matches()) return true;
        String back = path.startsWith("/admin/cleanup") ? "/admin/cleanup" : path.startsWith("/admin/import") ? "/admin/import"
                : path.startsWith("/admin/users") ? "/admin/users" : "/account/profile";
        FlashMap fm = RequestContextUtils.getOutputFlashMap(req);
        fm.put("err", "This action is switched off in the public demo (so that it stays clean for the next visitor).");
        fm.setTargetRequestPath(back);
        RequestContextUtils.saveOutputFlashMap(back, req, res);
        res.sendRedirect(req.getContextPath() + back);
        return false;
    }
}
