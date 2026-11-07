package com.psgitech.outpass.web;

import com.psgitech.outpass.domain.UserAccount;
import com.psgitech.outpass.repo.Repos;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class AuthController {
    private final Repos.Users users;
    private final PasswordEncoder encoder;
    private final com.psgitech.outpass.config.OutpassProperties props;
    private final org.springframework.beans.factory.ObjectProvider<org.springframework.security.oauth2.client.registration.ClientRegistrationRepository> registrations;

    @GetMapping("/login")
    public String login(Authentication auth, org.springframework.ui.Model m) {
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) return "redirect:/";
        if (props.isDemoMode()) return "redirect:/demo";      // public demo: one-click role chooser instead of login
        m.addAttribute("googleEnabled", registrations.getIfAvailable() != null);
        return "login";
    }

    @GetMapping("/")
    public String home(Authentication auth) {
        for (var a : auth.getAuthorities()) {
            switch (a.getAuthority()) {
                case "ROLE_STUDENT": return "redirect:/student";
                case "ROLE_TUTOR": return "redirect:/tutor";
                case "ROLE_HOD": return "redirect:/hod";
                case "ROLE_HEAD": return "redirect:/head";
                case "ROLE_SECURITY": return "redirect:/gate";
                case "ROLE_ADMIN": return "redirect:/admin";
                default: break;
            }
        }
        return "redirect:/login?error";
    }

    @GetMapping("/account/password")
    public String passwordForm() {
        return "account-password";
    }

    @PostMapping("/account/password")
    @Transactional
    public String changePassword(Principal p, @RequestParam String current, @RequestParam String next,
                                 @RequestParam String confirm, RedirectAttributes ra) {
        UserAccount u = users.findByEmailIgnoreCase(p.getName()).orElseThrow();
        if (!encoder.matches(current, u.getPasswordHash())) {
            ra.addFlashAttribute("err", "Your current password is incorrect.");
        } else if (next.length() < 8) {
            ra.addFlashAttribute("err", "The new password must be at least 8 characters.");
        } else if (!next.equals(confirm)) {
            ra.addFlashAttribute("err", "The new passwords do not match.");
        } else {
            u.setPasswordHash(encoder.encode(next));
            u.setMustChangePassword(false);
            ra.addFlashAttribute("msg", "Password changed successfully.");
            return "redirect:/";
        }
        return "redirect:/account/password";
    }
}
