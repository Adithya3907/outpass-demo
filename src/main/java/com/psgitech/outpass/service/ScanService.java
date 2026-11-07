package com.psgitech.outpass.service;

import com.psgitech.outpass.config.OutpassProperties;
import com.psgitech.outpass.domain.*;
import com.psgitech.outpass.repo.Repos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.Optional;

/** Gate verification. The "mark as used" step is one atomic conditional UPDATE. */
@Service
@RequiredArgsConstructor
public class ScanService {
    private final Repos.Requests requests;
    private final Repos.Scans scans;
    private final Repos.Gates gates;
    private final NotificationService notifications;
    private final AuditService audit;
    private final OutpassProperties props;
    private final Clock clock;

    public record Outcome(boolean ok, String message, OutpassRequest request) {}

    @Transactional
    public Outcome verify(String rawToken, Long gateId, UserAccount guard) {
        String token = rawToken == null ? "" : rawToken.trim();
        Gate gate = gateId == null ? null : gates.findById(gateId).orElse(null);
        Instant nowInstant = Instant.now(clock);

        if (token.isEmpty() || token.length() > 64) return reject(null, token, gate, guard, "Invalid QR");

        Optional<OutpassRequest> found = requests.findByQrToken(token);
        if (found.isEmpty()) return reject(null, token, gate, guard, "Invalid QR (or approval revoked)");
        OutpassRequest r = found.get();

        if (r.getStatus() == RequestStatus.USED) return reject(r, token, gate, guard, "Pass already used");
        if (r.getStatus() == RequestStatus.EXPIRED || nowInstant.isAfter(r.getExpiresAt()))
            return reject(r, token, gate, guard, "Expired pass (was valid until " + hm(r.getExpiresAt()) + ")");
        if (r.getStatus() != RequestStatus.APPROVED) return reject(r, token, gate, guard, "Pass not approved");
        if (nowInstant.isBefore(r.getValidFrom().minus(Duration.ofMinutes(props.getScanEarlyMinutes()))))
            return reject(r, token, gate, guard, "Too early - this pass works from " + hm(r.getValidFrom().minus(Duration.ofMinutes(props.getScanEarlyMinutes())))
                    + " to " + hm(r.getExpiresAt()));

        int updated = requests.markUsed(r.getId(), nowInstant, gate, RequestStatus.USED, RequestStatus.APPROVED);
        if (updated != 1) return reject(r, token, gate, guard, "Pass already used");

        OutpassRequest fresh = requests.findById(r.getId()).orElseThrow();
        GateScan s = new GateScan();
        s.setRequest(fresh);
        s.setTokenHint(hint(token));
        s.setGate(gate);
        s.setScannedBy(guard);
        s.setResult(ScanResult.SUCCESS);
        s.setScannedAt(nowInstant);
        scans.save(s);
        audit.log(guard.getEmail(), "GATE_EXIT", "OutpassRequest", fresh.getId(), fresh.getOutpassCode() + (gate != null ? " @ " + gate.getName() : ""));

        UserAccount student = fresh.getStudent().getUser();
        notifications.notify(student, NotificationType.GATE_EXIT, "OUTPASS VERIFIED",
                "Your outpass " + fresh.getOutpassCode() + " was scanned at the college gate. You may leave the campus.",
                "/student/requests/" + fresh.getId());
        return new Outcome(true, "VERIFIED - EXIT AUTHORIZED", fresh);
    }

    private Outcome reject(OutpassRequest r, String token, Gate gate, UserAccount guard, String reason) {
        GateScan s = new GateScan();
        s.setRequest(r);
        s.setTokenHint(hint(token));
        s.setGate(gate);
        s.setScannedBy(guard);
        s.setResult(ScanResult.REJECTED);
        s.setReason(reason.length() > 190 ? reason.substring(0, 190) : reason);
        scans.save(s);
        return new Outcome(false, reason, r);
    }

    private String hm(Instant i) {
        return java.time.format.DateTimeFormatter.ofPattern("hh:mm a", java.util.Locale.ENGLISH).format(i.atZone(clock.getZone()));
    }

    private static String hint(String token) {
        return token.length() <= 6 ? token : token.substring(0, 6) + "...";
    }

    @Transactional(readOnly = true)
    public List<GateScan> logs(LocalDate day) {
        Instant from = day.atStartOfDay(clock.getZone()).toInstant();
        return scans.between(from, from.plus(Duration.ofDays(1)), PageRequest.of(0, 1000));
    }
}
