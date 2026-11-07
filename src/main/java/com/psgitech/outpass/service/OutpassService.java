package com.psgitech.outpass.service;

import com.psgitech.outpass.config.OutpassProperties;
import com.psgitech.outpass.domain.*;
import com.psgitech.outpass.repo.Repos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core business rules: request submission, withdrawal, tutor / backup / HOD decisions,
 * revisions and QR-token issuing. Every authorisation check is enforced here on the
 * server - hiding buttons in the UI is never relied upon.
 */
@Service
@RequiredArgsConstructor
public class OutpassService {
    private static final SecureRandom RNG = new SecureRandom();
    private static final PageRequest TOP_500 = PageRequest.of(0, 500);
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
    private static final int MAX_CUSTOM_WORDS = 20;

    public static final String CUSTOM = "Custom";
    public static final List<String> REASONS = List.of("Feeling sick", "Medical appointment",
            "Going to home since exams got over", "Going to home", "To eat lunch", "Family Emergency", CUSTOM);

    /** Requests that block a new request whose validity window would overlap theirs. */
    private static final List<RequestStatus> BLOCKING = List.of(RequestStatus.PENDING, RequestStatus.MEET_IN_PERSON,
            RequestStatus.PENDING_HOD, RequestStatus.APPROVED, RequestStatus.USED);

    private final Repos.Requests requests;
    private final Repos.TutorAssignments tutorAssignments;
    private final Repos.HodAssignments hods;
    private final Repos.Faculty faculty;
    private final Repos.Revisions revisions;
    private final Repos.Students students;
    private final NotificationService notifications;
    private final AuditService audit;
    private final OutpassProperties props;
    private final Clock clock;

    // ------------------------------------------------------------------ student side

    @Transactional
    public OutpassRequest create(StudentProfile s, RequestForm f) {
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock).truncatedTo(ChronoUnit.MINUTES);

        String reason = resolveReason(f);
        if (f.getPermissionDate() == null || f.getExitTime() == null)
            throw new BusinessException("Date and planned exit time are required.");
        if (f.getPermissionDate().isBefore(today)) throw new BusinessException("Past dates are not allowed.");
        if (f.getPermissionDate().isAfter(today.plusDays(props.getMaxDaysAhead())))
            throw new BusinessException("You can request an outpass at most " + props.getMaxDaysAhead() + " days ahead.");
        if (f.getPermissionDate().equals(today) && f.getExitTime().isBefore(now))
            throw new BusinessException("The planned exit time has already passed.");
        if (f.getExpectedReturnTime() != null && !f.getExpectedReturnTime().isAfter(f.getExitTime()))
            throw new BusinessException("Expected return time must be after the exit time.");

        ZoneId zone = clock.getZone();
        Instant validFrom = LocalDateTime.of(f.getPermissionDate(), f.getExitTime()).atZone(zone).toInstant();
        Instant expiresAt = validFrom.plus(props.getValidityMinutes(), ChronoUnit.MINUTES);

        List<OutpassRequest> clash = requests.overlapping(s, BLOCKING, validFrom, expiresAt);
        if (!clash.isEmpty()) {
            OutpassRequest c = clash.get(0);
            throw new BusinessException("Your earlier request " + c.getOutpassCode() + " is valid from " + hm(c.getValidFrom())
                    + " until " + hm(c.getExpiresAt()) + ". Choose a planned exit time at or after " + hm(c.getExpiresAt())
                    + ", or at least " + props.getValidityMinutes() + " minutes before " + hm(c.getValidFrom()) + ".");
        }

        ClassSection cs = s.getClassSection();
        Department dept = cs.getDepartment();
        Escalation esc = f.getEscalation() == null ? Escalation.TUTOR : f.getEscalation();
        List<TutorAssignment> tutors = tutorAssignments.activeForClass(cs.getId(), today);

        OutpassRequest r = new OutpassRequest();
        r.setStudent(s);
        r.setClassSection(cs);
        r.setReason(reason);
        r.setValidFrom(validFrom);
        r.setExpiresAt(expiresAt);
        r.setPermissionDate(f.getPermissionDate());
        r.setExitTime(f.getExitTime());
        r.setExpectedReturnTime(f.getExpectedReturnTime());
        r.setDescription(blankToNull(f.getDescription()));
        r.setEscalation(esc);
        r.setAssignedTutorNames(tutors.stream().map(t -> t.getFaculty().getUser().getFullName()).collect(Collectors.joining(", ")));

        List<UserAccount> recipients = new ArrayList<>();
        switch (esc) {
            case TUTOR -> {
                if (tutors.isEmpty()) throw new BusinessException("No tutors are assigned to your class yet. Please contact the admin office.");
                tutors.forEach(t -> recipients.add(t.getFaculty().getUser()));
            }
            case BACKUP -> {
                List<FacultyProfile> pool = backupTutorsFor(cs, today);
                if (pool.isEmpty()) throw new BusinessException("No other tutors are available in your department.");
                pool.forEach(fp -> recipients.add(fp.getUser()));
            }
            case HOD -> {
                HodAssignment h = hods.findByDepartmentAndActiveTrue(dept)
                        .orElseThrow(() -> new BusinessException("No HOD is configured for your department."));
                recipients.add(h.getFaculty().getUser());
                r.setStatus(RequestStatus.PENDING_HOD);
            }
        }
        r = requests.save(r);
        record(r, s.getUser(), "SUBMITTED", null, r.getStatus(), null, 0);
        audit.log(s.getUser().getEmail(), "REQUEST_SUBMITTED", "OutpassRequest", r.getId(), r.getOutpassCode());

        for (UserAccount u : recipients) {
            notifications.notify(u, NotificationType.NEW_REQUEST, "New outpass request",
                    s.getUser().getFullName() + " (" + s.getRollNumber() + ") requested an outpass for "
                            + r.getPermissionDate() + " at " + r.getExitTime() + ". Reason: " + r.getReason(),
                    (u.getRole() == Role.HOD ? "/hod" : "/tutor") + "/requests/" + r.getId());
        }
        return r;
    }

    @Transactional
    public void withdraw(Long id, StudentProfile s) {
        OutpassRequest r = lock(id);
        if (!r.getStudent().getId().equals(s.getId())) throw new BusinessException("This request does not belong to you.");
        if (!r.isWithdrawable())
            throw new BusinessException("This request can no longer be deleted because it has already been acted upon.");
        RequestStatus prev = r.getStatus();
        r.setStatus(RequestStatus.WITHDRAWN);
        record(r, s.getUser(), "WITHDRAWN", prev, RequestStatus.WITHDRAWN, null, 0);
        audit.log(s.getUser().getEmail(), "REQUEST_WITHDRAWN", "OutpassRequest", r.getId(), r.getOutpassCode());
    }

    @Transactional(readOnly = true)
    public OutpassRequest getForStudent(Long id, StudentProfile s) {
        OutpassRequest r = requests.findById(id).orElseThrow(() -> new BusinessException("Request not found."));
        if (!r.getStudent().getId().equals(s.getId())) throw new BusinessException("Request not found.");
        return r;
    }

    // ------------------------------------------------------------------ tutor / backup side

    @Transactional(readOnly = true)
    public List<OutpassRequest> tutorInbox(UserAccount actor) {
        FacultyProfile fp = faculty.findByUser(actor).orElseThrow(() -> new BusinessException("Faculty profile missing."));
        Map<Long, OutpassRequest> merged = new LinkedHashMap<>();
        List<Long> classIds = tutorAssignments.activeClassIdsForFaculty(fp.getId(), LocalDate.now(clock));
        if (!classIds.isEmpty())
            requests.forClasses(Escalation.TUTOR, classIds, TOP_500).forEach(r -> merged.put(r.getId(), r));
        if (actor.getRole() == Role.TUTOR)
            requests.forDepartment(Escalation.BACKUP, fp.getDepartment().getId(), TOP_500).stream()
                    .filter(r -> !classIds.contains(r.getClassSection().getId()))     // own class = normal route
                    .forEach(r -> merged.put(r.getId(), r));
        List<OutpassRequest> out = new ArrayList<>(merged.values());
        out.sort(Comparator.comparing(OutpassRequest::getCreatedAt).reversed());
        return out;
    }

    @Transactional(readOnly = true)
    public OutpassRequest getForTutor(Long id, UserAccount actor) {
        OutpassRequest r = requests.findById(id).orElseThrow(() -> new BusinessException("Request not found."));
        assertTutorAccess(r, actor);
        return r;
    }

    @Transactional
    public void tutorDecide(Long id, UserAccount actor, TutorAction action, String remark) {
        OutpassRequest r = lock(id);
        assertTutorAccess(r, actor);

        if (r.getStatus() == RequestStatus.WITHDRAWN) throw new BusinessException("The student has withdrawn this request.");
        if (r.getStatus() == RequestStatus.USED)
            throw new BusinessException("The outpass has already been used at the gate. Decisions are locked.");
        if (r.getStatus() == RequestStatus.EXPIRED || !Instant.now(clock).isBefore(r.getExpiresAt()))
            throw new BusinessException("This request has expired (its validity window has passed). The student must submit a new request.");
        if (r.getDecisionOwner() != null && !r.getDecisionOwner().getId().equals(actor.getId()))
            throw new BusinessException("This request is already being handled by " + r.getDecisionOwner().getFullName() + ".");

        RequestStatus prev = r.getStatus();
        RequestStatus next = switch (action) {
            case APPROVE -> RequestStatus.APPROVED;
            case DECLINE -> RequestStatus.DECLINED;
            case MEET -> RequestStatus.MEET_IN_PERSON;
            case FORWARD_HOD -> RequestStatus.PENDING_HOD;
        };
        if (prev == next) throw new BusinessException("No change: the request is already in that state.");

        boolean isRevision = prev == RequestStatus.APPROVED || prev == RequestStatus.DECLINED || prev == RequestStatus.PENDING_HOD;
        if (isRevision) {
            if (r.getRevisionCount() >= props.getMaxRevisions())
                throw new BusinessException("You have used all " + props.getMaxRevisions() + " permitted revisions for this request.");
            r.setRevisionCount(r.getRevisionCount() + 1);
        }

        revokeApproval(r);                       // any change invalidates the old QR and any HOD approval
        r.setDecisionOwner(actor);
        r.setDecisionRemark(blankToNull(remark));
        r.setDecidedAt(Instant.now(clock));
        r.setStatus(next);
        if (next == RequestStatus.APPROVED) issueToken(r);

        record(r, actor, action.name(), prev, next, remark, r.getRevisionCount());
        audit.log(actor.getEmail(), "TUTOR_" + action.name(), "OutpassRequest", r.getId(),
                prev + " -> " + next + (isRevision ? " (revision " + r.getRevisionCount() + ")" : ""));

        UserAccount student = r.getStudent().getUser();
        switch (next) {
            case APPROVED -> notifications.notify(student, NotificationType.DECISION, "Outpass approved",
                    "Your outpass " + r.getOutpassCode() + " was approved by " + actor.getFullName() + ". Your QR code is valid until " + hm(r.getExpiresAt()) + ".",
                    "/student/requests/" + r.getId());
            case DECLINED -> notifications.notify(student, NotificationType.DECISION, "Outpass declined",
                    "Your outpass " + r.getOutpassCode() + " was declined." + (remark != null && !remark.isBlank() ? " Reason: " + remark : ""),
                    "/student/requests/" + r.getId());
            case MEET_IN_PERSON -> notifications.notify(student, NotificationType.DECISION, "Please meet your tutor",
                    actor.getFullName() + " has asked you to meet in person about outpass " + r.getOutpassCode() + ".",
                    "/student/requests/" + r.getId());
            case PENDING_HOD -> {
                notifications.notify(student, NotificationType.DECISION, "Forwarded to HOD",
                        actor.getFullName() + " endorsed your request and forwarded it to the HOD.", "/student/requests/" + r.getId());
                hods.findByDepartmentAndActiveTrue(r.getClassSection().getDepartment()).ifPresent(h ->
                        notifications.notify(h.getFaculty().getUser(), NotificationType.NEW_REQUEST, "Outpass awaiting your approval",
                                r.getStudent().getUser().getFullName() + " (" + r.getStudent().getRollNumber()
                                        + ") - endorsed by " + actor.getFullName() + ".", "/hod/requests/" + r.getId()));
            }
            default -> { }
        }
        otherTutorsRefresh(r, actor);
    }

    // ------------------------------------------------------------------ HOD side

    @Transactional(readOnly = true)
    public Department hodDepartment(UserAccount actor) {
        FacultyProfile fp = faculty.findByUser(actor).orElseThrow(() -> new BusinessException("Faculty profile missing."));
        return hods.findByFacultyAndActiveTrue(fp).map(HodAssignment::getDepartment)
                .orElseThrow(() -> new BusinessException("You are not assigned as HOD of any department. Contact the admin."));
    }

    @Transactional(readOnly = true)
    public List<OutpassRequest> departmentRequests(UserAccount hod) {
        return requests.allInDepartment(hodDepartment(hod).getId(), TOP_500);
    }

    @Transactional(readOnly = true)
    public OutpassRequest getForHod(Long id, UserAccount hod) {
        OutpassRequest r = requests.findById(id).orElseThrow(() -> new BusinessException("Request not found."));
        if (!r.getClassSection().getDepartment().getId().equals(hodDepartment(hod).getId()))
            throw new BusinessException("Request not found.");
        return r;
    }

    @Transactional
    public void hodDecide(Long id, UserAccount hod, boolean approve, String remark) {
        Department dept = hodDepartment(hod);
        OutpassRequest r = lock(id);
        if (!r.getClassSection().getDepartment().getId().equals(dept.getId()))
            throw new BusinessException("Request not found.");
        if (r.getStatus() != RequestStatus.PENDING_HOD)
            throw new BusinessException("This request is not awaiting HOD approval.");
        if (!Instant.now(clock).isBefore(r.getExpiresAt()))
            throw new BusinessException("This request has expired (its validity window has passed).");

        RequestStatus prev = r.getStatus();
        RequestStatus next = approve ? RequestStatus.APPROVED : RequestStatus.DECLINED;
        r.setHodApprover(hod);
        r.setHodRemark(blankToNull(remark));
        r.setHodDecidedAt(Instant.now(clock));
        r.setStatus(next);
        if (approve) issueToken(r);

        record(r, hod, approve ? "HOD_APPROVE" : "HOD_DECLINE", prev, next, remark, r.getRevisionCount());
        audit.log(hod.getEmail(), approve ? "HOD_APPROVE" : "HOD_DECLINE", "OutpassRequest", r.getId(), prev + " -> " + next);

        UserAccount student = r.getStudent().getUser();
        notifications.notify(student, NotificationType.DECISION, approve ? "Outpass approved by HOD" : "Outpass declined by HOD",
                "Outpass " + r.getOutpassCode() + (approve ? " has been approved. Your QR code is ready." : " was declined by the HOD."),
                "/student/requests/" + r.getId());
        if (r.getDecisionOwner() != null)
            notifications.notify(r.getDecisionOwner(), NotificationType.HOD_UPDATE, "HOD decision recorded",
                    "HOD " + (approve ? "approved" : "declined") + " the request of " + student.getFullName() + ".",
                    "/tutor/requests/" + r.getId());
    }

    @Transactional(readOnly = true)
    public List<StudentProfile> searchStudents(Department dept, String q) {
        String term = q == null ? "" : q.trim();
        return students.searchInDepartment(dept.getId(), term, PageRequest.of(0, 100));
    }

    @Transactional(readOnly = true)
    public StudentProfile studentInDepartment(Long studentId, Department dept) {
        StudentProfile s = students.findById(studentId).orElseThrow(() -> new BusinessException("Student not found."));
        if (!s.getClassSection().getDepartment().getId().equals(dept.getId())) throw new BusinessException("Student not found.");
        return s;
    }

    @Transactional(readOnly = true)
    public List<OutpassRequest> historyOf(StudentProfile s) {
        return requests.findByStudentOrderByCreatedAtDesc(s);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRevision> timeline(OutpassRequest r) {
        return revisions.findByRequestOrderByCreatedAtAsc(r);
    }

    // ------------------------------------------------------------------ helpers

    private void assertTutorAccess(OutpassRequest r, UserAccount actor) {
        FacultyProfile fp = faculty.findByUser(actor).orElseThrow(() -> new BusinessException("Request not found."));
        boolean ok = switch (r.getEscalation()) {
            case TUTOR -> tutorAssignments.activeClassIdsForFaculty(fp.getId(), LocalDate.now(clock))
                    .contains(r.getClassSection().getId());
            case BACKUP -> actor.getRole() == Role.TUTOR
                    && fp.getDepartment().getId().equals(r.getClassSection().getDepartment().getId())
                    && !tutorAssignments.activeClassIdsForFaculty(fp.getId(), LocalDate.now(clock)).contains(r.getClassSection().getId());
            case HOD -> false;
        };
        if (!ok) throw new BusinessException("Request not found.");
    }

    private OutpassRequest lock(Long id) {
        return requests.lockById(id).orElseThrow(() -> new BusinessException("Request not found."));
    }

    /** Backup tutors = the other tutors of the student's own department (not the class's assigned tutors). */
    @Transactional(readOnly = true)
    public List<FacultyProfile> backupTutorsFor(ClassSection cs, LocalDate date) {
        Set<Long> assigned = tutorAssignments.activeForClass(cs.getId(), date).stream()
                .map(t -> t.getFaculty().getId()).collect(Collectors.toSet());
        return faculty.findByDepartmentAndRole(cs.getDepartment().getId(), Role.TUTOR).stream()
                .filter(f -> !assigned.contains(f.getId())).toList();
    }

    /** The class tutors (with contact details) that applied on the day of this request. */
    @Transactional(readOnly = true)
    public List<FacultyProfile> tutorsOf(ClassSection cs, LocalDate date) {
        return tutorAssignments.activeForClass(cs.getId(), date).stream().map(TutorAssignment::getFaculty).toList();
    }

    private String resolveReason(RequestForm f) {
        String sel = f.getReason() == null ? "" : f.getReason().trim();
        if (sel.isEmpty()) throw new BusinessException("Please select a reason.");
        if (!REASONS.contains(sel)) throw new BusinessException("Invalid reason selected.");
        if (!CUSTOM.equals(sel)) return sel;
        String text = f.getCustomReason() == null ? "" : f.getCustomReason().trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) throw new BusinessException("Please type your reason.");
        if (text.split(" ").length > MAX_CUSTOM_WORDS)
            throw new BusinessException("Please write your reason in less than " + MAX_CUSTOM_WORDS + " words.");
        if (text.length() > 255) throw new BusinessException("The reason is too long.");
        return text;
    }

    private String hm(Instant i) {
        return HM.format(i.atZone(clock.getZone()));
    }

    private void issueToken(OutpassRequest r) {
        if (!Instant.now(clock).isBefore(r.getExpiresAt()))
            throw new BusinessException("This request has expired (its validity window has passed). The student must submit a new request.");
        byte[] b = new byte[32];
        RNG.nextBytes(b);
        r.setQrToken(Base64.getUrlEncoder().withoutPadding().encodeToString(b));
        r.setApprovedAt(Instant.now(clock));
    }

    private void revokeApproval(OutpassRequest r) {
        r.setQrToken(null);
        r.setApprovedAt(null);
        r.setHodApprover(null);
        r.setHodRemark(null);
        r.setHodDecidedAt(null);
    }

    private void record(OutpassRequest r, UserAccount actor, String action, RequestStatus prev, RequestStatus next, String remark, int revNo) {
        ApprovalRevision a = new ApprovalRevision();
        a.setRequest(r);
        a.setActor(actor);
        a.setAction(action);
        a.setPreviousStatus(prev);
        a.setNewStatus(next);
        a.setRemark(blankToNull(remark));
        a.setRevisionNo(revNo);
        revisions.save(a);
    }

    private void otherTutorsRefresh(OutpassRequest r, UserAccount actor) {
        if (r.getEscalation() != Escalation.TUTOR) return;
        tutorAssignments.activeForClass(r.getClassSection().getId(), LocalDate.now(clock)).stream()
                .map(t -> t.getFaculty().getUser())
                .filter(u -> !u.getId().equals(actor.getId()))
                .forEach(notifications::refresh);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
