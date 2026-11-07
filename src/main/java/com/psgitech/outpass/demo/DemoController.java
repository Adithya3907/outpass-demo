package com.psgitech.outpass.demo;

import com.psgitech.outpass.domain.UserAccount;
import com.psgitech.outpass.repo.Repos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

/** PUBLIC DEMO ONLY: choose a role and enter with one click - no password, no Google, no real account. */
@Controller
@ConditionalOnProperty(prefix = "outpass", name = "demo-mode", havingValue = "true")
@RequiredArgsConstructor
public class DemoController {
    public record Persona(String key, String title, String email, String blurb) {}

    private static final List<Persona> PERSONAS = List.of(
            new Persona("student", "Student - Asha Demo", "student1" + DemoSeeder.D, "Submit an outpass request, see the QR with its live countdown, download the PDF."),
            new Persona("tutor", "Tutor - Dr. Anita Rao", "tutor1" + DemoSeeder.D, "Approve, decline, ask to meet or forward to the HOD. Change a decision (twice allowed)."),
            new Persona("tutor2", "Second tutor - Mr. Kiran Das", "tutor2" + DemoSeeder.D, "The other tutor of the class: sees the same requests."),
            new Persona("backup", "Backup tutor - Ms. Priya Nair", "backup" + DemoSeeder.D, "A tutor of the branch who can act when the class tutors are away."),
            new Persona("hod", "Head of Department - Dr. Suresh Menon", "hod" + DemoSeeder.D, "Approve requests forwarded by tutors; branch overview, reports, CSV downloads."),
            new Persona("head", "Principal (view only)", "head" + DemoSeeder.D, "College-wide view of every student and request; reports and downloads."),
            new Persona("security", "Security guard", "security" + DemoSeeder.D, "The gate screen: scan (paste) a pass and see green VERIFIED or red DENIED."),
            new Persona("admin", "Admin", "admin" + DemoSeeder.D, "People, classes, tutors, imports and CSV formats (destructive actions are switched off in the demo)."));

    private final Repos.Users users;
    private final DemoResetService reset;

    @GetMapping("/demo")
    public String choose(Model m) {
        m.addAttribute("personas", PERSONAS);
        return "demo/choose";
    }

    @PostMapping("/demo/reset")
    public String resetNow(org.springframework.web.servlet.mvc.support.RedirectAttributes ra) {
        boolean done = reset.resetNow();
        ra.addFlashAttribute("msg", done ? "All demo data was cleared and fresh sample data was loaded." : "The demo was reset a moment ago - please wait a few seconds.");
        return "redirect:/";
    }

    @PostMapping("/demo/enter/{key}")
    public String enter(@PathVariable String key, HttpServletRequest req, HttpServletResponse res) {
        Persona p = PERSONAS.stream().filter(x -> x.key().equals(key)).findFirst().orElse(null);
        UserAccount u = p == null ? null : users.findByEmailIgnoreCase(p.email()).orElse(null);
        if (u == null) return "redirect:/demo";
        if (req.getSession(false) != null) req.changeSessionId();
        var auth = UsernamePasswordAuthenticationToken.authenticated(u.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        new HttpSessionSecurityContextRepository().saveContext(ctx, req, res);
        return "redirect:/";
    }
}
