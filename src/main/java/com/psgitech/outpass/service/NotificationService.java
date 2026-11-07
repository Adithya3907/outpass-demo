package com.psgitech.outpass.service;

import com.psgitech.outpass.config.OutpassProperties;
import com.psgitech.outpass.domain.Notification;
import com.psgitech.outpass.domain.NotificationPref;
import com.psgitech.outpass.domain.NotificationType;
import com.psgitech.outpass.domain.UserAccount;
import com.psgitech.outpass.repo.Repos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Every notification is stored in the in-app list. Phone/desktop push and e-mail are sent only if the
 * user has not switched that category off in Settings.
 */
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final Repos.Notifications repo;
    private final Repos.NotificationPrefs prefs;
    private final SseHub hub;
    private final MailService mail;
    private final PushSender push;
    private final OutpassProperties props;

    public void notify(UserAccount user, NotificationType type, String title, String message, String link) {
        Notification n = new Notification();
        n.setUser(user);
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setLink(link);
        repo.save(n);

        boolean wantPush = true, wantEmail = true;
        NotificationPref p = prefs.findByUserAndType(user, type).orElse(null);
        if (p != null) { wantPush = p.isPushEnabled(); wantEmail = p.isEmailEnabled(); }

        Long uid = user.getId();
        String email = user.getEmail();
        boolean fPush = wantPush, fMail = wantEmail;
        afterCommit(() -> {
            hub.push(email, "notify", title);
            if (fPush) push.send(uid, title, message, link);
            if (fMail) mail.send(email, title, message + (link != null ? "\n\nOpen: " + props.getBaseUrl() + link : ""));
        });
    }

    /** Tell open browser tabs of this user to refresh, without creating a notification. */
    public void refresh(UserAccount user) {
        String email = user.getEmail();
        afterCommit(() -> hub.push(email, "refresh", "1"));
    }

    private void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { r.run(); }
            });
        } else {
            r.run();
        }
    }
}
