package com.psgitech.outpass.demo;

import com.psgitech.outpass.domain.*;
import com.psgitech.outpass.repo.Repos;
import com.psgitech.outpass.service.AuditService;
import com.psgitech.outpass.service.PeopleService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

/**
 * PUBLIC DEMO ONLY. Creates a small, completely fictional college: 10 students ("... Demo"), 3 tutors, an HOD,
 * a head, a security guard and an admin, plus sample requests in every status so every screen has something to show.
 * No real person's data is ever used here.
 */
@Service
@ConditionalOnProperty(prefix = "outpass", name = "demo-mode", havingValue = "true")
@RequiredArgsConstructor
public class DemoSeeder {
    public static final String D = "@demo.outpass";
    private static final SecureRandom RNG = new SecureRandom();

    private final PeopleService people;
    private final Repos.Users users;
    private final Repos.Students students;
    private final Repos.Requests requests;
    private final Repos.Revisions revisions;
    private final Repos.Scans scans;
    private final Repos.Gates gates;
    private final AuditService audit;
    private final Clock clock;

    @Transactional
    public void seed() {
        if (users.existsByEmailIgnoreCase("student1" + D)) return;
        String a = "demo";
        int adm = people.academicStartYear() - 1;          // second-year class "2-A"

        people.saveStaff(new PeopleService.StaffData("Dr. Anita Rao", "tutor1" + D, "9000000011", "CSE", "TUTOR", "2-A"), a);
        people.saveStaff(new PeopleService.StaffData("Mr. Kiran Das", "tutor2" + D, "9000000012", "CSE", "TUTOR", "2-A"), a);
        people.saveStaff(new PeopleService.StaffData("Ms. Priya Nair", "backup" + D, "9000000013", "CSE", "TUTOR", ""), a);
        people.saveStaff(new PeopleService.StaffData("Dr. Suresh Menon", "hod" + D, "9000000014", "CSE", "HOD", ""), a);
        people.saveSimple("Principal (Demo)", "head" + D, Role.HEAD, false, a);
        people.saveSimple("Gate Security (Demo)", "security" + D, Role.SECURITY, false, a);
        people.saveSimple("Demo Admin", "admin" + D, Role.ADMIN, false, a);

        String[] first = {"Asha", "Bala", "Chitra", "Deepa", "Esha", "Farid", "Gita", "Hari", "Indu", "Jaya"};
        for (int i = 0; i < first.length; i++) {
            people.saveStudent(new PeopleService.StudentData(first[i] + " Demo", String.format("DEMO%04d", i + 1), "student" + (i + 1) + D,
                    adm, "CSE", "A", "90000002" + String.format("%02d", i + 1), true), a);
        }

        UserAccount t1 = users.findByEmailIgnoreCase("tutor1" + D).orElseThrow();
        UserAccount t2 = users.findByEmailIgnoreCase("tutor2" + D).orElseThrow();
        UserAccount hod = users.findByEmailIgnoreCase("hod" + D).orElseThrow();
        UserAccount guard = users.findByEmailIgnoreCase("security" + D).orElseThrow();
        Gate gate = gates.findAllByOrderByName().get(0);
        Instant now = Instant.now(clock);

        // Bala: waiting for a tutor
        make(stu(2), "Medical appointment", now.plus(40, ChronoUnit.MINUTES), RequestStatus.PENDING, null, null, false, null);
        // Chitra & Deepa: approved - QR ready to scan at the gate page
        make(stu(3), "To eat lunch", now.plus(10, ChronoUnit.MINUTES), RequestStatus.APPROVED, t1, null, true, null);
        make(stu(4), "Going to home", now.plus(50, ChronoUnit.MINUTES), RequestStatus.APPROVED, t2, "Take care.", true, null);
        // Esha: declined
        make(stu(5), "Feeling sick", now.plus(30, ChronoUnit.MINUTES), RequestStatus.DECLINED, t2, "Please bring a medical note first.", false, null);
        // Farid: forwarded to the HOD
        make(stu(6), "Family Emergency", now.plus(60, ChronoUnit.MINUTES), RequestStatus.PENDING_HOD, t1, "Genuine case, please approve.", false, null);
        // Gita: already used at the gate
        OutpassRequest used = make(stu(7), "Going to home since exams got over", now.minus(25, ChronoUnit.MINUTES), RequestStatus.USED, t1, null, true, null);
        used.setUsedAt(now.minus(15, ChronoUnit.MINUTES));
        used.setUsedGate(gate);
        GateScan gs = new GateScan();
        gs.setRequest(used); gs.setGate(gate); gs.setScannedBy(guard); gs.setResult(ScanResult.SUCCESS);
        gs.setTokenHint(used.getQrToken().substring(0, 6) + "..."); gs.setScannedAt(used.getUsedAt());
        scans.save(gs);
        // Hari: never decided, so it expired
        make(stu(8), "Personal work", now.minus(3, ChronoUnit.HOURS), RequestStatus.EXPIRED, null, null, false, null);
        audit.log("demo", "DEMO_SEEDED", "Demo", null, "fictional sample data created");
    }

    private StudentProfile stu(int n) {
        return students.findByUser(users.findByEmailIgnoreCase("student" + n + D).orElseThrow()).orElseThrow();
    }

    private OutpassRequest make(StudentProfile sp, String reason, Instant exit, RequestStatus st, UserAccount owner, String remark, boolean token, Object unused) {
        ZonedDateTime z = exit.atZone(clock.getZone());
        Instant from = exit.truncatedTo(ChronoUnit.MINUTES);
        OutpassRequest r = new OutpassRequest();
        r.setStudent(sp);
        r.setClassSection(sp.getClassSection());
        r.setReason(reason);
        r.setPermissionDate(z.toLocalDate());
        r.setExitTime(z.toLocalTime().truncatedTo(ChronoUnit.MINUTES));
        r.setValidFrom(from);
        r.setExpiresAt(from.plus(75, ChronoUnit.MINUTES));
        r.setAssignedTutorNames("Dr. Anita Rao, Mr. Kiran Das");
        r.setEscalation(Escalation.TUTOR);
        r.setStatus(st);
        Instant now = Instant.now(clock);
        if (owner != null) { r.setDecisionOwner(owner); r.setDecisionRemark(remark); r.setDecidedAt(now); }
        if (token) { r.setQrToken(newToken()); r.setApprovedAt(now); }
        r = requests.save(r);
        history(r, sp.getUser(), "SUBMITTED", null, RequestStatus.PENDING, null);
        if (owner != null) {
            String action = switch (st) {
                case APPROVED, USED -> "APPROVE";
                case DECLINED -> "DECLINE";
                case PENDING_HOD -> "FORWARD_HOD";
                default -> "UPDATE";
            };
            history(r, owner, action, RequestStatus.PENDING, st == RequestStatus.USED ? RequestStatus.APPROVED : st, remark);
        }
        return r;
    }

    private void history(OutpassRequest r, UserAccount actor, String action, RequestStatus prev, RequestStatus next, String remark) {
        ApprovalRevision a = new ApprovalRevision();
        a.setRequest(r); a.setActor(actor); a.setAction(action); a.setPreviousStatus(prev); a.setNewStatus(next); a.setRemark(remark);
        revisions.save(a);
    }

    private static String newToken() {
        byte[] b = new byte[32];
        RNG.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static List<String> personaEmails() {
        return List.of("student1" + D, "tutor1" + D, "tutor2" + D, "backup" + D, "hod" + D, "head" + D, "security" + D, "admin" + D);
    }
}
