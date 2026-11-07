package com.psgitech.outpass.web;

import com.psgitech.outpass.config.OutpassProperties;
import com.psgitech.outpass.domain.*;
import com.psgitech.outpass.repo.Repos;
import com.psgitech.outpass.service.PushSender;
import com.psgitech.outpass.service.VapidService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.*;
import java.util.regex.Pattern;

/** "My account": contact details, notification settings (what to be notified about, push to phone). */
@Controller
@RequiredArgsConstructor
public class AccountController {
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9 \\-]{8,18}[0-9]$");

    private final Repos.Users users;
    private final Repos.Students students;
    private final Repos.Faculty faculty;
    private final Repos.NotificationPrefs prefs;
    private final Repos.PushSubscriptions subscriptions;
    private final VapidService vapid;
    private final PushSender pushSender;
    private final OutpassProperties props;

    private UserAccount me(Principal p) {
        return users.findByEmailIgnoreCase(p.getName()).orElseThrow();
    }

    // ------------------------------------------------------------ profile (phone)

    @GetMapping("/account/profile")
    public String profile(Principal p, Model m) {
        UserAccount u = me(p);
        m.addAttribute("phone", phoneOf(u));
        m.addAttribute("needsPhone", needsPhone(u));
        m.addAttribute("showPhone", u.getRole() == Role.STUDENT || u.getRole() == Role.TUTOR || u.getRole() == Role.HOD);
        m.addAttribute("phoneRequired", u.getRole() == Role.STUDENT);
        return "account/profile";
    }

    @PostMapping("/account/profile")
    @Transactional
    public String saveProfile(Principal p, @RequestParam String phone, HttpServletRequest req, RedirectAttributes ra) {
        UserAccount u = me(p);
        String ph = phone == null ? "" : phone.trim();
        boolean student = u.getRole() == Role.STUDENT;
        if (ph.isEmpty() && !student) ph = null;
        if (ph != null && !PHONE.matcher(ph).matches()) {
            ra.addFlashAttribute("err", "Please enter a valid phone number (10-15 digits).");
            return "redirect:/account/profile";
        }
        final String phFinal = ph;
        if (student) students.findByUser(u).ifPresent(s -> s.setPhone(phFinal));
        else if (u.getRole() == Role.TUTOR || u.getRole() == Role.HOD) faculty.findByUser(u).ifPresent(f -> f.setPhone(phFinal));
        var session = req.getSession(false);
        if (session != null) session.removeAttribute("gateOk");
        ra.addFlashAttribute("msg", "Contact details saved.");
        return "redirect:/";
    }

    private boolean needsPhone(UserAccount u) {
        return u.getRole() == Role.STUDENT;
    }

    private String phoneOf(UserAccount u) {
        if (u.getRole() == Role.STUDENT) return students.findByUser(u).map(StudentProfile::getPhone).orElse("");
        if (u.getRole() == Role.TUTOR || u.getRole() == Role.HOD) return faculty.findByUser(u).map(FacultyProfile::getPhone).orElse("");
        return "";
    }

    // ------------------------------------------------------------ notification settings

    public record Row(NotificationType type, boolean push, boolean email) {}

    @GetMapping("/account/notifications")
    public String notificationSettings(Principal p, Model m) {
        UserAccount u = me(p);
        Map<NotificationType, NotificationPref> saved = new EnumMap<>(NotificationType.class);
        prefs.findByUser(u).forEach(x -> saved.put(x.getType(), x));
        List<Row> rows = new ArrayList<>();
        for (NotificationType t : NotificationType.forRole(u.getRole())) {
            NotificationPref x = saved.get(t);
            rows.add(new Row(t, x == null || x.isPushEnabled(), x == null || x.isEmailEnabled()));
        }
        m.addAttribute("rows", rows);
        m.addAttribute("vapidKey", vapid.getPublicKey());
        m.addAttribute("devices", subscriptions.countByUser(u));
        m.addAttribute("mailEnabled", props.isMailEnabled());
        return "account/notifications";
    }

    @PostMapping("/account/notifications")
    @Transactional
    public String saveNotificationSettings(Principal p, HttpServletRequest req, RedirectAttributes ra) {
        UserAccount u = me(p);
        for (NotificationType t : NotificationType.forRole(u.getRole())) {
            NotificationPref x = prefs.findByUserAndType(u, t).orElseGet(() -> {
                NotificationPref n = new NotificationPref();
                n.setUser(u);
                n.setType(t);
                return n;
            });
            x.setPushEnabled(req.getParameter("push_" + t.name()) != null);
            x.setEmailEnabled(req.getParameter("email_" + t.name()) != null);
            prefs.save(x);
        }
        ra.addFlashAttribute("msg", "Notification settings saved.");
        return "redirect:/account/notifications";
    }

    // ------------------------------------------------------------ push subscriptions (called by push.js)

    @PostMapping("/account/push/subscribe")
    @ResponseBody
    @Transactional
    public String subscribe(Principal p, @RequestParam String endpoint, @RequestParam String p256dh,
                            @RequestParam String auth, @RequestHeader(value = "User-Agent", required = false) String ua) {
        UserAccount u = me(p);
        PushSubscription s = subscriptions.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
        s.setUser(u);
        s.setEndpoint(endpoint);
        s.setP256dh(p256dh);
        s.setAuth(auth);
        s.setUserAgent(ua != null && ua.length() > 240 ? ua.substring(0, 240) : ua);
        subscriptions.save(s);
        return "ok";
    }

    @PostMapping("/account/push/unsubscribe")
    @ResponseBody
    @Transactional
    public String unsubscribe(Principal p, @RequestParam String endpoint) {
        UserAccount u = me(p);
        subscriptions.findByEndpoint(endpoint).filter(s -> s.getUser().getId().equals(u.getId())).ifPresent(subscriptions::delete);
        return "ok";
    }

    @PostMapping("/account/push/test")
    @ResponseBody
    public String test(Principal p) {
        UserAccount u = me(p);
        pushSender.send(u.getId(), "Test notification", "Notifications are working on this device.", "/account/notifications");
        return "sent";
    }
}
