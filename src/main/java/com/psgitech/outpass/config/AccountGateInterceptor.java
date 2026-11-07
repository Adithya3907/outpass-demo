package com.psgitech.outpass.config;

import com.psgitech.outpass.domain.Role;
import com.psgitech.outpass.domain.StudentProfile;
import com.psgitech.outpass.domain.UserAccount;
import com.psgitech.outpass.repo.Repos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * First-login gate: a user with a temporary password must set a new one, and students / tutors / HODs
 * (students only - staff phone numbers come from the college records) must add their phone number.
 */
@Component
@RequiredArgsConstructor
public class AccountGateInterceptor implements HandlerInterceptor {
    private final Repos.Users users;
    private final Repos.Students students;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        if (req.getUserPrincipal() == null) return true;
        HttpSession session = req.getSession();
        UserAccount u = users.findByEmailIgnoreCase(req.getUserPrincipal().getName()).orElse(null);
        // The admin changed this person's e-mail, deleted or disabled the account: end the old session.
        if (u == null || !u.isEnabled()) {
            session.invalidate();
            res.sendRedirect(req.getContextPath() + "/login");
            return false;
        }
        if (session.getAttribute("gateOk") != null) return true;
        if (u.isLocalLogin() && u.isMustChangePassword()) {
            res.sendRedirect(req.getContextPath() + "/account/password");
            return false;
        }
        boolean needsPhone = false;
        if (u.getRole() == Role.STUDENT) {
            needsPhone = students.findByUser(u).map(StudentProfile::getPhone).map(String::isBlank).orElse(true);
        }
        if (needsPhone) {
            res.sendRedirect(req.getContextPath() + "/account/profile");
            return false;
        }
        session.setAttribute("gateOk", Boolean.TRUE);
        return true;
    }
}
