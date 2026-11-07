package com.psgitech.outpass.web;

import com.psgitech.outpass.domain.*;
import com.psgitech.outpass.repo.Repos;
import com.psgitech.outpass.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.*;

/**
 * CSV downloads for HODs (own department), Heads and Admins (everything). The student file uses exactly the
 * same columns as the student import, so a download can be edited and uploaded back.
 */
@Controller
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController {
    private final PeopleService people;
    private final OutpassService outpass;
    private final Repos.Users users;
    private final Repos.Requests requests;
    private final Repos.Departments departments;

    private static final String STUDENT_HEADER = "name,register_number,email,admission_year,branch,section,phone,active\r\n";
    private static final String STAFF_HEADER = "name,email,phone,branch,role,mentor_classes\r\n";

    /** HODs are always limited to their own department; Heads and Admins may choose. */
    private String scopeDept(Authentication auth, Principal p, String requested) {
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_HOD"))) {
            UserAccount me = users.findByEmailIgnoreCase(p.getName()).orElseThrow();
            return outpass.hodDepartment(me).getCode();
        }
        return requested == null ? "" : requested;
    }

    private ResponseEntity<byte[]> csv(String name, List<String> lines) {
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name + "-" + LocalDate.now() + ".csv").build().toString())
                .body(Csv.bytes(lines));
    }

    @GetMapping("/students.csv")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> students(@RequestParam(required = false) String dept, @RequestParam(required = false) Integer year,
                                           @RequestParam(required = false) String section, @RequestParam(required = false) String q,
                                           @RequestParam(required = false) String status, Authentication auth, Principal p) {
        String d = scopeDept(auth, p, dept);
        List<String> lines = new ArrayList<>();
        lines.add(STUDENT_HEADER);
        for (StudentProfile s : people.studentsFor(new PeopleService.Filter(q, null, d, year, section, status))) {
            ClassSection c = s.getClassSection();
            lines.add(Csv.row(s.getUser().getFullName(), s.getRollNumber(), s.getUser().getEmail(), people.admissionYearOf(c.getAcademicYear()),
                    c.getDepartment().getCode(), c.getSection(), s.getPhone(), s.getUser().isEnabled()));
        }
        return csv("students", lines);
    }

    @GetMapping("/staff.csv")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> staff(@RequestParam(required = false) String dept, @RequestParam(required = false) String role,
                                        @RequestParam(required = false) String q, Authentication auth, Principal p) {
        String d = scopeDept(auth, p, dept);
        List<String> lines = new ArrayList<>();
        lines.add(STAFF_HEADER);
        for (FacultyProfile f : people.facultyFor(new PeopleService.Filter(q, role, d, null, null, null)))
            lines.add(Csv.row(f.getUser().getFullName(), f.getUser().getEmail(), f.getPhone(), f.getDepartment().getCode(),
                    f.getUser().getRole().name(), people.mentorClassText(f)));
        return csv("staff", lines);
    }

    @GetMapping("/requests.csv")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> requests(@RequestParam(required = false) String dept, @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           Authentication auth, Principal p) {
        String d = scopeDept(auth, p, dept);
        String term = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<String> lines = new ArrayList<>();
        lines.add("outpass_id,register_number,name,branch,year,section,reason,planned_exit_date,planned_exit_time,qr_expires_at,status,handled_by,decision_revisions,gate_exit_time,gate\r\n");
        var fmt = new Fmt();
        for (OutpassRequest r : requests.allRecent(org.springframework.data.domain.PageRequest.of(0, 50000))) {
            if (!d.isBlank() && !r.getClassSection().getDepartment().getCode().equalsIgnoreCase(d)) continue;
            if (status != null && !status.isBlank() && !r.getStatus().name().equals(status)) continue;
            if (from != null && r.getPermissionDate().isBefore(from)) continue;
            if (to != null && r.getPermissionDate().isAfter(to)) continue;
            if (!term.isEmpty() && !r.getStudent().getRollNumber().toLowerCase(Locale.ROOT).contains(term)
                    && !r.getStudent().getUser().getFullName().toLowerCase(Locale.ROOT).contains(term)) continue;
            lines.add(Csv.row(r.getOutpassCode(), r.getStudent().getRollNumber(), r.getStudent().getUser().getFullName(),
                    r.getClassSection().getDepartment().getCode(), r.getClassSection().getAcademicYear(), r.getClassSection().getSection(),
                    r.getReason(), r.getPermissionDate(), r.getExitTime(), fmt.dt(r.getExpiresAt()), r.getStatus().name(),
                    r.getApprovingFaculty() == null ? "" : r.getApprovingFaculty().getFullName(), r.getRevisionCount(),
                    r.getUsedAt() == null ? "" : fmt.dt(r.getUsedAt()), r.getUsedGate() == null ? "" : r.getUsedGate().getName()));
        }
        return csv("outpass-records", lines);
    }

    // ---- templates (admin only): header + example rows, in the permanent import format

    @GetMapping("/template/{kind}.csv")
    public ResponseEntity<byte[]> template(@PathVariable String kind) {
        List<String> lines = new ArrayList<>();
        switch (kind) {
            case "students" -> {
                lines.add(STUDENT_HEADER);
                lines.add(Csv.row("Aarav Kumar", "715526104001", "student1@example.edu", 2026, "CSE", "A", "9876543210", "true"));
                lines.add(Csv.row("Divya Raj", "715525104002", "student2@example.edu", 2025, "CSE", "A", "", ""));
            }
            case "staff" -> {
                lines.add(STAFF_HEADER);
                lines.add(Csv.row("Dr. Meena S", "meena@psgitech.ac.in", "9876500001", "CSE", "TUTOR", "1-A;2-A"));
                lines.add(Csv.row("Dr. Ravi K", "hodcse@psgitech.ac.in", "9876500002", "CSE", "HOD", ""));
            }
            case "heads" -> {
                lines.add("name,email\r\n");
                lines.add(Csv.row("Principal", "principal@psgitech.ac.in"));
            }
            default -> { return ResponseEntity.notFound().build(); }
        }
        return csv("template-" + kind, lines);
    }
}
