package com.psgitech.outpass.web;

import com.psgitech.outpass.domain.*;
import com.psgitech.outpass.repo.Repos;
import com.psgitech.outpass.service.AdminService;
import com.psgitech.outpass.service.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService admin;
    private final Repos.Users users;
    private final Repos.Departments departments;
    private final Repos.ClassSections classes;
    private final Repos.Faculty faculty;
    private final Repos.TutorAssignments tutors;
    private final Repos.HodAssignments hods;
    private final Repos.Gates gates;
    private final Repos.Requests requests;
    private final Repos.Students students;
    private final Repos.AuditLogs auditLogs;
    private final com.psgitech.outpass.service.InstitutionImportService importer;
    private final com.psgitech.outpass.config.OutpassProperties props;

    private interface Action { void run() throws Exception; }

    private String exec(RedirectAttributes ra, String redirect, String ok, Action a) {
        try {
            a.run();
            ra.addFlashAttribute("msg", ok);
        } catch (BusinessException e) {
            ra.addFlashAttribute("err", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("err", "Operation failed: " + e.getMessage());
        }
        return "redirect:" + redirect;
    }

    // ------------------------------------------------------------ overview
    @GetMapping
    public String overview(Model m) {
        m.addAttribute("students", users.countByRole(Role.STUDENT));
        m.addAttribute("tutors", users.countByRole(Role.TUTOR) + users.countByRole(Role.HOD));
        m.addAttribute("security", users.countByRole(Role.SECURITY));
        m.addAttribute("pending", requests.countByStatus(RequestStatus.PENDING) + requests.countByStatus(RequestStatus.PENDING_HOD)
                + requests.countByStatus(RequestStatus.MEET_IN_PERSON));
        m.addAttribute("approved", requests.countByStatus(RequestStatus.APPROVED));
        m.addAttribute("used", requests.countByStatus(RequestStatus.USED));
        m.addAttribute("classCount", classes.findAll().size());
        return "admin/overview";
    }

    // ------------------------------------------------------------ structure
    @GetMapping("/structure")
    public String structure(Model m) {
        m.addAttribute("departments", departments.findAllByOrderByCodeAsc());
        m.addAttribute("classes", classes.findAllOrdered());
        m.addAttribute("gates", gates.findAllByOrderByName());
        return "admin/structure";
    }

    @PostMapping("/departments")
    public String addDept(@RequestParam String code, @RequestParam String name, Principal p, RedirectAttributes ra) {
        return exec(ra, "/admin/structure", "Department added.", () -> admin.createDepartment(code, name, p.getName()));
    }

    @PostMapping("/classes")
    public String addClass(@RequestParam Long deptId, @RequestParam int year, @RequestParam String section,
                           @RequestParam int semester, Principal p, RedirectAttributes ra) {
        return exec(ra, "/admin/structure", "Class added.", () -> admin.createClass(deptId, year, section, semester, p.getName()));
    }

    @PostMapping("/gates")
    public String addGate(@RequestParam String name, Principal p, RedirectAttributes ra) {
        return exec(ra, "/admin/structure", "Gate added.", () -> admin.createGate(name, p.getName()));
    }

    @PostMapping("/gates/{id}/toggle")
    public String toggleGate(@PathVariable Long id, Principal p, RedirectAttributes ra) {
        return exec(ra, "/admin/structure", "Gate updated.", () -> admin.toggleGate(id, p.getName()));
    }

    // ------------------------------------------------------------ assignments
    @GetMapping("/assignments")
    public String assignments(Model m) {
        m.addAttribute("classes", classes.findAllOrdered());
        m.addAttribute("faculty", faculty.findAllOrdered());
        m.addAttribute("tutorAssignments", tutors.findAllOrdered());
        m.addAttribute("hodList", hods.findByActiveTrue());
        m.addAttribute("today", java.time.LocalDate.now());
        return "admin/assignments";
    }

    @PostMapping("/assignments/tutor")
    public String assignTutor(@RequestParam Long classId, @RequestParam Long facultyId, Principal p, RedirectAttributes ra) {
        return exec(ra, "/admin/assignments", "Tutor assigned.", () -> admin.assignTutor(classId, facultyId, p.getName()));
    }

    @PostMapping("/assignments/tutor/{id}/end")
    public String endTutor(@PathVariable Long id, Principal p, RedirectAttributes ra) {
        return exec(ra, "/admin/assignments", "Tutor assignment ended (history preserved).", () -> admin.endTutor(id, p.getName()));
    }

    @PostMapping("/assignments/hod")
    public String setHod(@RequestParam Long facultyId, Principal p, RedirectAttributes ra) {
        return exec(ra, "/admin/assignments", "HOD assigned.", () -> admin.setHod(facultyId, p.getName()));
    }

    // ------------------------------------------------------------ audit
    @GetMapping("/audit")
    public String audit(Model m) {
        m.addAttribute("logs", auditLogs.findTop300ByOrderByIdDesc());
        return "admin/audit";
    }

    // ------------------------------------------------------------ college data import (data/ folder)
    @GetMapping("/import")
    public String importPage(Model m) {
        m.addAttribute("dir", java.nio.file.Path.of(props.getImportDir()).toAbsolutePath().toString());
        return "admin/import";
    }

    @PostMapping("/import/run")
    public String runImport(Principal p, Model m) {
        m.addAttribute("dir", java.nio.file.Path.of(props.getImportDir()).toAbsolutePath().toString());
        try {
            var rep = importer.run(java.nio.file.Path.of(props.getImportDir()), p.getName());
            m.addAttribute("counts", rep.counts);
            m.addAttribute("errors", rep.errors);
        } catch (BusinessException e) {
            m.addAttribute("err", e.getMessage());
        } catch (Exception e) {
            m.addAttribute("err", "Import failed: " + e.getMessage());
        }
        return "admin/import";
    }
}
