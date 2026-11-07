package com.psgitech.outpass.service;

import com.psgitech.outpass.domain.PushSubscription;
import com.psgitech.outpass.domain.UserAccount;
import com.psgitech.outpass.repo.Repos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.List;

/** Sends Web Push messages (phone / desktop notifications, even when the app is closed). */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushSender {
    private final Repos.PushSubscriptions subs;
    private final Repos.Users users;
    private final VapidService vapid;
    private volatile nl.martijndwars.webpush.PushService service;

    private nl.martijndwars.webpush.PushService service() throws Exception {
        if (service == null) {
            synchronized (this) {
                if (service == null) {
                    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) Security.addProvider(new BouncyCastleProvider());
                    service = new nl.martijndwars.webpush.PushService(vapid.getPublicKey(), vapid.getPrivateKey(), "mailto:admin@psgitech.ac.in");
                }
            }
        }
        return service;
    }

    @Async
    public void send(Long userId, String title, String body, String url) {
        try {
            UserAccount u = users.findById(userId).orElse(null);
            if (u == null) return;
            List<PushSubscription> list = subs.findByUser(u);
            if (list.isEmpty()) return;
            byte[] payload = ("{\"title\":" + q(title) + ",\"body\":" + q(body) + ",\"url\":" + q(url == null ? "/" : url) + "}")
                    .getBytes(StandardCharsets.UTF_8);
            for (PushSubscription s : list) {
                try {
                    var n = new nl.martijndwars.webpush.Notification(s.getEndpoint(), s.getP256dh(), s.getAuth(), payload);
                    var resp = service().send(n);
                    int code = resp.getStatusLine().getStatusCode();
                    if (code == 404 || code == 410) subs.delete(s);          // device unsubscribed / uninstalled
                    else if (code >= 400) log.warn("Push service answered {} for user {}", code, userId);
                } catch (Exception e) {
                    log.warn("Push failed for user {}: {}", userId, e.toString());
                }
            }
        } catch (Exception e) {
            log.warn("Push error: {}", e.toString());
        }
    }

    private static String q(String s) {
        if (s == null) return "\"\"";
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> { }
                case '\t' -> b.append(' ');
                default -> { if (c < 0x20) b.append(' '); else b.append(c); }
            }
        }
        return b.append('"').toString();
    }
}
