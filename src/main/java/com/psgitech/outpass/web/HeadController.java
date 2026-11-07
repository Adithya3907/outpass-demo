package com.psgitech.outpass.web;

import com.psgitech.outpass.domain.*;
import com.psgitech.outpass.repo.Repos;
import com.psgitech.outpass.service.BusinessException;
import com.psgitech.outpass.service.OutpassService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** Institution heads (Principal, Dean, Secretary): read-only view of every department and student. */
@Controller
@RequestMapping("/head")
@RequiredArgsConstructor
public class HeadController {
    public record Row(String label, long count) {}

    private final Repos.Requests requests;
    private final Repos.Students students;
    private final Repos.Scans scans;
    private final Repos.Departments departments;
    private final OutpassService service;

    @GetMapping
    @Transactional(readOnly = true)
    public String dashboard(@RequestParam(required = false, defaultValue = "") String status,
                            @RequestParam(required = false, defaultValue = "") String dept,
                            @RequestParam(required = false, defaultValue = "") String q, Model m) {
        List<OutpassRequest> list = requests.allRecent(PageRequest.of(0, 1000));
        String term = q.trim().toLowerCase(Locale.ROOT);
        list = list.stream()
                .filter(r -> status.isBlank() || r.getStatus().name().equals(status))
                .filter(r -> dept.isBlank() || r.getClassSection().getDepartment().getCode().equals(dept))
                .filter(r -> term.isEmpty() || r.getStudent().getRollNumber().toLowerCase(Locale.ROOT).contains(term)
                        || r.getStudent().getUser().getFullName().toLowerCase(Locale.ROOT).contains(term))
                .limit(200).toList();
        m.addAttribute("list", list);
        m.addAttribute("statuses", RequestStatus.values());
        m.addAttribute("departments", departments.findAllByOrderByCodeAsc());
        m.addAttribute("selectedStatus", status);
        m.addAttribute("selectedDept", dept);
        m.addAttribute("q", q);
        return "head/dashboard";
    }

    @GetMapping("/requests/{id}")
    @Transactional(readOnly = true)
    public String detail(@PathVariable Long id, Model m) {
        OutpassRequest r = requests.findById(id).orElseThrow(() -> new BusinessException("Request not found."));
        m.addAttribute("r", r);
        m.addAttribute("timeline", service.timeline(r));
        m.addAttribute("actions", new TutorAction[0]);
        m.addAttribute("postUrl", "/head");
        m.addAttribute("backUrl", "/head");
        m.addAttribute("canAct", false);
        m.addAttribute("revisionsLeft", 0);
        m.addAttribute("banner", DecisionBanner.of(r, null, 2));
        m.addAttribute("lockedMessage", "View only. Decisions are made by tutors and heads of department.");
        return "staff/request";
    }

    @GetMapping("/students")
    @Transactional(readOnly = true)
    public String students(@RequestParam(required = false, defaultValue = "") String q, Model m) {
        m.addAttribute("q", q);
        m.addAttribute("results", q.isBlank() ? List.<StudentProfile>of() : students.search(q.trim(), PageRequest.of(0, 100)));
        return "head/students";
    }

    @GetMapping("/students/{id}")
    @Transactional(readOnly = true)
    public String student(@PathVariable Long id, Model m) {
        StudentProfile s = students.findById(id).orElseThrow(() -> new BusinessException("Student not found."));
        m.addAttribute("student", s);
        m.addAttribute("history", service.historyOf(s));
        m.addAttribute("scans", scans.forStudent(s.getId(), PageRequest.of(0, 100)));
        return "head/student";
    }

    @GetMapping("/reports")
    @Transactional(readOnly = true)
    public String reports(Model m) {
        Map<RequestStatus, Long> byStatus = new EnumMap<>(RequestStatus.class);
        for (Object[] row : requests.countAllByStatus()) byStatus.put((RequestStatus) row[0], (Long) row[1]);
        List<Row> statusRows = new ArrayList<>();
        long total = 0;
        for (RequestStatus st : RequestStatus.values()) {
            long n = byStatus.getOrDefault(st, 0L);
            total += n;
            statusRows.add(new Row(st.getLabel(), n));
        }
        Map<String, Long> byDept = new TreeMap<>();
        for (Object[] row : requests.countByDepartmentAndStatus()) byDept.merge((String) row[0], (Long) row[2], Long::sum);
        List<Row> deptRows = new ArrayList<>();
        byDept.forEach((k, v) -> deptRows.add(new Row(k, v)));
        m.addAttribute("statusRows", statusRows);
        m.addAttribute("deptRows", deptRows);
        m.addAttribute("total", total);
        return "head/reports";
    }
}
