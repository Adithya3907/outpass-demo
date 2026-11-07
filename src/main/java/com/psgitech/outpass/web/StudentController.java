package com.psgitech.outpass.web;

import com.psgitech.outpass.config.OutpassProperties;
import com.psgitech.outpass.domain.*;
import com.psgitech.outpass.repo.Repos;
import com.psgitech.outpass.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentController {
    public record DayOption(String iso, String name, String label) {}

    private final Repos.Users users;
    private final Repos.Students students;
    private final Repos.Scans scans;
    private final OutpassService service;
    private final QrService qr;
    private final PdfService pdf;
    private final OutpassProperties props;
    private final Clock clock;

    private StudentProfile me(Principal p) {
        UserAccount u = users.findByEmailIgnoreCase(p.getName()).orElseThrow();
        return students.findByUser(u).orElseThrow(() -> new BusinessException("Your student profile is not set up. Contact the admin office."));
    }

    @GetMapping
    public String dashboard(Principal p, Model m) {
        StudentProfile s = me(p);
        m.addAttribute("student", s);
        m.addAttribute("requests", service.historyOf(s));
        m.addAttribute("tutors", service.tutorsOf(s.getClassSection(), java.time.LocalDate.now(clock)));
        return "student/dashboard";
    }

    @GetMapping("/new")
    public String form(Principal p, Model m) {
        RequestForm f = new RequestForm();
        f.setPermissionDate(LocalDate.now(clock));
        LocalTime now = LocalTime.now(clock);
        LocalTime t = now.plusMinutes(15).truncatedTo(ChronoUnit.MINUTES);
        f.setExitTime(t.isBefore(now) ? LocalTime.of(23, 59) : t);
        return formView(p, m, f);
    }

    @PostMapping("/new")
    public String submit(Principal p, @ModelAttribute("form") RequestForm form, BindingResult br, Model m, RedirectAttributes ra) {
        StudentProfile s = me(p);
        if (br.hasErrors()) {
            m.addAttribute("error", "Please check the date and time values you entered.");
            return formView(p, m, form);
        }
        try {
            OutpassRequest r = service.create(s, form);
            ra.addFlashAttribute("msg", "Request submitted. You will be notified as soon as your tutor responds.");
            return "redirect:/student/requests/" + r.getId();
        } catch (BusinessException e) {
            m.addAttribute("error", e.getMessage());
            return formView(p, m, form);
        }
    }

    private String formView(Principal p, Model m, RequestForm f) {
        LocalDate today = LocalDate.now(clock);
        DateTimeFormatter label = DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.ENGLISH);
        List<DayOption> days = new ArrayList<>();
        String[] names = {"Today", "Tomorrow", "Day after tomorrow"};
        for (int i = 0; i <= Math.min(props.getMaxDaysAhead(), 2); i++)
            days.add(new DayOption(today.plusDays(i).toString(), names[i], label.format(today.plusDays(i))));
        m.addAttribute("form", f);
        m.addAttribute("days", days);
        m.addAttribute("reasons", OutpassService.REASONS);
        m.addAttribute("today", today.toString());
        m.addAttribute("maxDate", today.plusDays(props.getMaxDaysAhead()).toString());
        m.addAttribute("student", me(p));
        m.addAttribute("escalations", Escalation.values());
        return "student/new";
    }

    @GetMapping("/requests/{id}")
    public String detail(@PathVariable Long id, Principal p, Model m) {
        OutpassRequest r = service.getForStudent(id, me(p));
        m.addAttribute("r", r);
        m.addAttribute("scanFrom", r.getValidFrom().minus(props.getScanEarlyMinutes(), java.time.temporal.ChronoUnit.MINUTES));
        m.addAttribute("timeline", service.timeline(r));
        m.addAttribute("tutors", service.tutorsOf(r.getClassSection(), r.getPermissionDate()));
        return "student/detail";
    }

    @PostMapping("/requests/{id}/withdraw")
    public String withdraw(@PathVariable Long id, Principal p, RedirectAttributes ra) {
        try {
            service.withdraw(id, me(p));
            ra.addFlashAttribute("msg", "Your request has been deleted.");
            return "redirect:/student";
        } catch (BusinessException e) {
            ra.addFlashAttribute("err", e.getMessage());
            return "redirect:/student/requests/" + id;
        }
    }

    @GetMapping("/requests/{id}/qr.png")
    public ResponseEntity<byte[]> qr(@PathVariable Long id, Principal p) {
        OutpassRequest r = service.getForStudent(id, me(p));
        if (r.getStatus() != RequestStatus.APPROVED || r.getQrToken() == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .body(qr.png(r.getQrToken(), 480));
    }

    @GetMapping("/requests/{id}/pass.pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, Principal p) {
        OutpassRequest r = service.getForStudent(id, me(p));
        if (r.getStatus() != RequestStatus.APPROVED && r.getStatus() != RequestStatus.USED)
            throw new BusinessException("The PDF is available only after the outpass is approved.");
        byte[] data = pdf.outpassPdf(r);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(r.getOutpassCode() + ".pdf").build().toString())
                .body(data);
    }
}
