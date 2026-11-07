package com.psgitech.outpass.demo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.psgitech.outpass.config.OutpassProperties;

/**
 * PUBLIC DEMO ONLY. Seeds the fictional data at start-up and wipes + re-creates it every few minutes, so whatever
 * visitors do (or break) disappears and the next visitor always meets a clean demo.
 */
@Service
@Order(4)
@ConditionalOnProperty(prefix = "outpass", name = "demo-mode", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DemoResetService implements ApplicationRunner {
    private static final String[] TABLES = {"notification", "notification_pref", "push_subscription", "gate_scan", "approval_revision",
            "outpass_request", "tutor_assignment", "backup_tutor_assignment", "hod_assignment", "student_profile", "faculty_profile",
            "class_section", "audit_log"};

    private final DemoSeeder seeder;
    private final PlatformTransactionManager tx;
    private final OutpassProperties props;

    @PersistenceContext
    private EntityManager em;

    private volatile long nextReset;
    private volatile long lastReset;

    @Override
    public void run(ApplicationArguments args) {
        seeder.seed();
        schedule();
        log.warn("PUBLIC DEMO MODE: fictional data loaded; it resets every {} minutes.", props.getDemoResetMinutes());
    }

    /** "Clear all data now" button: wipe and reload the fictional data immediately (ignored if pressed again within 10 seconds). */
    public synchronized boolean resetNow() {
        if (System.currentTimeMillis() - lastReset < 10_000) return false;
        doReset();
        return true;
    }

    private void doReset() {
        try {
            new TransactionTemplate(tx).executeWithoutResult(s -> wipe());
            seeder.seed();
            log.info("Demo data reset.");
        } catch (Exception e) {
            log.warn("Demo reset failed: {}", e.toString());
        }
        lastReset = System.currentTimeMillis();
        schedule();
    }

    @Scheduled(initialDelayString = "#{${outpass.demo-reset-minutes:30} * 60000}", fixedDelayString = "#{${outpass.demo-reset-minutes:30} * 60000}")
    public synchronized void scheduledReset() {
        doReset();
    }

    private void wipe() {
        em.createNativeQuery("select set_config('outpass.allow_purge', 'on', true)").getSingleResult();
        for (String t : TABLES) em.createNativeQuery("delete from " + t).executeUpdate();
        em.createNativeQuery("delete from user_account where lower(email) <> 'admin@psgitech.local'").executeUpdate();
        em.clear();
    }

    private void schedule() {
        nextReset = System.currentTimeMillis() + props.getDemoResetMinutes() * 60_000L;
    }

    public long getNextResetMillis() {
        return nextReset;
    }
}
