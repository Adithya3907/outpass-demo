package com.psgitech.outpass.service;

import com.psgitech.outpass.domain.NotificationType;
import com.psgitech.outpass.domain.OutpassRequest;
import com.psgitech.outpass.domain.RequestStatus;
import com.psgitech.outpass.repo.Repos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Passes (and undecided requests) expire on their own once planned exit time + validity (75 min) has passed.
 * The status is changed in the database, so every portal shows it. Runs every 30 s and, throttled to once
 * per 5 s, whenever any signed-in user loads a page - so the display never lags behind the clock.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpiryService {
    private static final List<RequestStatus> LIVE = List.of(RequestStatus.PENDING, RequestStatus.MEET_IN_PERSON,
            RequestStatus.PENDING_HOD, RequestStatus.APPROVED);

    private final Repos.Requests requests;
    private final NotificationService notifications;
    private final AuditService audit;
    private final PlatformTransactionManager txManager;
    private final Clock clock;
    private volatile long lastRun = 0;

    @Scheduled(fixedRate = 30_000)
    public void scheduled() {
        run();
    }

    public void runThrottled() {
        long now = System.currentTimeMillis();
        if (now - lastRun < 5_000) return;
        run();
    }

    private void run() {
        lastRun = System.currentTimeMillis();
        try {
            new TransactionTemplate(txManager).executeWithoutResult(status -> expireDue());
        } catch (Exception e) {
            log.warn("Expiry sweep failed: {}", e.getMessage());
        }
    }

    private void expireDue() {
        Instant now = Instant.now(clock);
        for (OutpassRequest r : requests.findDue(LIVE, now)) {
            RequestStatus prev = r.getStatus();
            if (requests.markExpired(r.getId(), LIVE, now, RequestStatus.EXPIRED) != 1) continue;   // lost a race (e.g. used)
            audit.log("system", "REQUEST_EXPIRED", "OutpassRequest", r.getId(), r.getOutpassCode() + ": " + prev + " -> EXPIRED");
            if (prev == RequestStatus.APPROVED) {
                notifications.notify(r.getStudent().getUser(), NotificationType.EXPIRY, "Outpass expired",
                        "Your outpass " + r.getOutpassCode() + " has expired and can no longer be scanned. Submit a new request if you still need to leave.",
                        "/student/requests/" + r.getId());
            } else {
                notifications.refresh(r.getStudent().getUser());
            }
            if (r.getDecisionOwner() != null) notifications.refresh(r.getDecisionOwner());
        }
    }
}
