package com.psgitech.outpass.web;

import com.psgitech.outpass.config.OutpassProperties;
import com.psgitech.outpass.domain.StudentProfile;
import com.psgitech.outpass.domain.UserAccount;
import com.psgitech.outpass.repo.Repos;
import com.psgitech.outpass.service.SseHub;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.security.Principal;
import java.time.Duration;
import java.util.Locale;

@Controller
@RequiredArgsConstructor
public class CommonController {
    private final Repos.Users users;
    private final Repos.Students students;
    private final Repos.Notifications notifications;
    private final SseHub hub;
    private final OutpassProperties props;

    /** Live updates channel (Server-Sent Events). */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @org.springframework.web.bind.annotation.ResponseBody
    public SseEmitter events(Principal p) {
        return hub.subscribe(p.getName());     // no database access: must never hold a connection
    }

    @GetMapping("/notifications")
    @Transactional
    public String notifications(Principal p, Model m) {
        UserAccount u = users.findByEmailIgnoreCase(p.getName()).orElseThrow();
        m.addAttribute("items", notifications.findTop50ByUserOrderByCreatedAtDesc(u));
        m.addAttribute("unread", 0L);
        // Items are loaded first; the flag flip below happens after rendering data is captured.
        java.util.List<Long> unreadIds = notifications.findTop50ByUserOrderByCreatedAtDesc(u).stream()
                .filter(n -> !n.isSeen()).map(n -> n.getId()).toList();
        m.addAttribute("unreadIds", unreadIds);
        notifications.markAllSeen(u);
        return "notifications";
    }

    /** Student photo, looked up as <photo-dir>/<ROLL>.jpg|jpeg|png . 404 when absent (UI shows initials). */
    @GetMapping("/photos/{roll}")
    public ResponseEntity<Resource> photo(@PathVariable String roll, Authentication auth) {
        if (!roll.matches("[A-Za-z0-9_-]{1,30}")) return ResponseEntity.notFound().build();
        boolean isStudent = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STUDENT"));
        if (isStudent) {
            UserAccount u = users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
            StudentProfile sp = students.findByUser(u).orElse(null);
            if (sp == null || !sp.getRollNumber().equalsIgnoreCase(roll)) return ResponseEntity.status(403).build();
        }
        for (String ext : new String[]{"jpg", "jpeg", "png"}) {
            File f = new File(props.getPhotoDir(), roll.toUpperCase(Locale.ROOT) + "." + ext);
            if (f.isFile()) {
                return ResponseEntity.ok()
                        .contentType(ext.equals("png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG)
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                        .body(new FileSystemResource(f));
            }
        }
        return ResponseEntity.notFound().build();
    }
}
