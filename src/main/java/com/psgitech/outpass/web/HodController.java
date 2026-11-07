package com.psgitech.outpass.web;

import com.psgitech.outpass.domain.*;
import com.psgitech.outpass.repo.Repos;
import com.psgitech.outpass.service.BusinessException;
import com.psgitech.outpass.service.OutpassService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.*;

@Controller
@RequestMapping("/hod")
@RequiredArgsConstructor
public class HodController {
    public record Row(String label, long count) {}

    private final Repos.Users users;
    private final Repos.Requests requests;
    private final Repos.ClassSections classes;
    private final Repos.Scans scans;
    private final OutpassService service;

    private UserAccount me(Principal p) {
        return users.findByEmailIgnoreCase(p.getName()).orElseThrow();
    }

    @GetMapping
    public String dashboard(Principal p, Model m, @RequestParam(required = false) String status) {
        UserAccount me = me(p);
        Department d = service.hodDepartment(me);
        List<OutpassRequest> all = service.departmentRequests(me);
        m.addAttribute("dept", d);
        m.addAttribute("pending", all.stream().filter(r -> r.getStatus() == RequestStatus.PENDING_HOD).toList());
        List<OutpassRequest> rest = all.stream().filter(r -> r.getStatus() != RequestStatus.PENDING_HOD).toList();
        if (status != null && !status.isBlank()) {
            try {
                RequestStatus st = RequestStatus.valueOf(status);
                rest = rest.stream().filter(r -> r.getStatus() == st).toList();
            } catch (IllegalArgumentException ignored) { }
        }
        m.addAttribute("recent", rest.size() > 100 ? rest.subList(0, 100) : rest);
        m.addAttribute("statuses", RequestStatus.values());
        m.addAttribute("selectedStatus", status == null ? "" : status);
        return "hod/dashboard";
    }

    @GetMapping("/requests/{id}")
    public String detail(@PathVariable Long id, Principal p, Model m) {
        OutpassRequest r = service.getForHod(id, me(p));
        boolean canAct = r.getStatus() == RequestStatus.PENDING_HOD;
        m.addAttribute("r", r);
        m.addAttribute("timeline", service.timeline(r));
        m.addAttribute("actions", new TutorAction[]{TutorAction.APPROVE, TutorAction.DECLINE});
        m.addAttribute("postUrl", "/hod/requests/" + id + "/decision");
        m.addAttribute("backUrl", "/hod");
        m.addAttribute("canAct", canAct);
        m.addAttribute("revisionsLeft", 0);
        m.addAttribute("banner", DecisionBanner.of(r, me(p), 2));
        m.addAttribute("lockedMessage", canAct ? null : "This request is not awaiting HOD approval.");
        return "staff/request";
    }

    @PostMapping("/requests/{id}/decision")
    public String decide(@PathVariable Long id, @RequestParam TutorAction action,
                         @RequestParam(required = false) String remark, Principal p, RedirectAttributes ra) {
        try {
            if (action != TutorAction.APPROVE && action != TutorAction.DECLINE) throw new BusinessException("Invalid action.");
            service.hodDecide(id, me(p), action == TutorAction.APPROVE, remark);
            ra.addFlashAttribute("msg", "Decision recorded.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("err", e.getMessage());
        }
        return "redirect:/hod/requests/" + id;
    }

    @GetMapping("/students")
    public String students(@RequestParam(required = false, defaultValue = "") String q, Principal p, Model m) {
        Department d = service.hodDepartment(me(p));
        m.addAttribute("dept", d);
        m.addAttribute("q", q);
        m.addAttribute("results", service.searchStudents(d, q));
        return "hod/students";
    }

    @GetMapping("/students/{id}")
    public String student(@PathVariable Long id, Principal p, Model m) {
        Department d = service.hodDepartment(me(p));
        StudentProfile s = service.studentInDepartment(id, d);
        m.addAttribute("student", s);
        m.addAttribute("history", service.historyOf(s));
        m.addAttribute("scans", scans.forStudent(s.getId(), PageRequest.of(0, 100)));
        return "hod/student";
    }

    @GetMapping("/reports")
    public String reports(Principal p, Model m) {
        Department d = service.hodDepartment(me(p));
        Map<RequestStatus, Long> byStatus = new EnumMap<>(RequestStatus.class);
        for (Object[] row : requests.countByStatus(d.getId())) byStatus.put((RequestStatus) row[0], (Long) row[1]);
        Map<Long, Long> byClassId = new HashMap<>();
        for (Object[] row : requests.countByClass(d.getId())) byClassId.put((Long) row[0], (Long) row[1]);
        List<Row> byClass = new ArrayList<>();
        for (ClassSection c : classes.findAllById(byClassId.keySet())) byClass.add(new Row(c.getLabel(), byClassId.get(c.getId())));
        byClass.sort(Comparator.comparing(Row::label));
        List<Row> statusRows = new ArrayList<>();
        long total = 0;
        for (RequestStatus st : RequestStatus.values()) {
            long n = byStatus.getOrDefault(st, 0L);
            total += n;
            statusRows.add(new Row(st.getLabel(), n));
        }
        m.addAttribute("dept", d);
        m.addAttribute("statusRows", statusRows);
        m.addAttribute("classRows", byClass);
        m.addAttribute("total", total);
        return "hod/reports";
    }
}
