package com.psgitech.outpass.web;

import com.psgitech.outpass.domain.*;
import com.psgitech.outpass.repo.Repos;
import com.psgitech.outpass.service.BusinessException;
import com.psgitech.outpass.service.ScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Security-desk interface (desktop + USB 2D scanner). URL prefix /gate. */
@Controller
@RequestMapping("/gate")
@RequiredArgsConstructor
public class GateController {
    private final Repos.Users users;
    private final Repos.Gates gates;
    private final Repos.Students students;
    private final Repos.Scans scans;
    private final Repos.Requests requests;
    private final com.psgitech.outpass.config.OutpassProperties props;
    private final ScanService scanService;
    private final Clock clock;

    private UserAccount me(Principal p) {
        return users.findByEmailIgnoreCase(p.getName()).orElseThrow();
    }

    @GetMapping
    public String scanner(Model m) {
        m.addAttribute("gates", gates.findByActiveTrueOrderByName());
        if (props.isDemoMode()) m.addAttribute("demoPasses", requests.findByStatusOrderByValidFromAsc(RequestStatus.APPROVED));
        return "gate/scanner";
    }

    /** Called by HTMX each time the USB scanner "types" a token and presses Enter. Returns only the result fragment. */
    @PostMapping("/scan")
    public String scan(@RequestParam String token, @RequestParam(required = false) Long gateId, Principal p, Model m) {
        ScanService.Outcome o = scanService.verify(token, gateId, me(p));
        m.addAttribute("ok", o.ok());
        m.addAttribute("message", o.message());
        m.addAttribute("r", o.request());
        m.addAttribute("gateName", gateId == null ? null : gates.findById(gateId).map(Gate::getName).orElse(null));
        return "gate/result :: result";
    }

    @GetMapping("/logs")
    public String logs(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                       @RequestParam(required = false, defaultValue = "") String q, Model m) {
        LocalDate day = date != null ? date : LocalDate.now(clock);
        List<GateScan> list = scanService.logs(day);
        String term = q.trim().toLowerCase(Locale.ROOT);
        if (!term.isEmpty()) {
            list = list.stream().filter(s -> s.getRequest() != null &&
                    (s.getRequest().getStudent().getRollNumber().toLowerCase(Locale.ROOT).contains(term)
                            || s.getRequest().getStudent().getUser().getFullName().toLowerCase(Locale.ROOT).contains(term))).toList();
        }
        m.addAttribute("scans", list);
        m.addAttribute("day", day.toString());
        m.addAttribute("q", q);
        return "gate/logs";
    }

    @GetMapping("/students")
    public String students(@RequestParam(required = false, defaultValue = "") String q, Model m) {
        m.addAttribute("q", q);
        m.addAttribute("results", q.isBlank() ? List.<StudentProfile>of() : students.search(q.trim(), PageRequest.of(0, 50)));
        return "gate/students";
    }

    @GetMapping("/students/{id}")
    public String student(@PathVariable Long id, Model m) {
        StudentProfile s = students.findById(id).orElseThrow(() -> new BusinessException("Student not found."));
        m.addAttribute("student", s);
        m.addAttribute("scans", scans.forStudent(id, PageRequest.of(0, 100)));
        return "gate/student";
    }
}
