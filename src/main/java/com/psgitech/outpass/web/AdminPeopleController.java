package com.psgitech.outpass.web;

import com.psgitech.outpass.domain.*;
import com.psgitech.outpass.repo.Repos;
import com.psgitech.outpass.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.util.*;

/** Admin: find, add, edit, delete people; CSV upload; data clean-up; format guide. */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminPeopleController {
    private static final int PAGE_SIZE = 50;

    private final PeopleService people;
    private final PurgeService purge;
    private final Repos.Users users;
    private final Repos.Departments departments;
    private final Repos.ClassSections classes;
    private final Repos.Students students;
    private final Repos.Faculty faculty;
    private final Repos.TutorAssignments tutors;
    private final Repos.HodAssignments hods;
    private final java.time.Clock clock;

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

    private static boolean confirmed(String c) { return c != null && c.trim().equals("DELETE"); }

    // ------------------------------------------------------------ list / search

    @GetMapping("/users")
    public String list(@RequestParam(required = false, defaultValue = "") String q, @RequestParam(required = false, defaultValue = "") String role,
                       @RequestParam(required = false, defaultValue = "") String dept, @RequestParam(required = false) Integer year,
                       @RequestParam(required = false, defaultValue = "") String section, @RequestParam(required = false, defaultValue = "") String status,
                       @RequestParam(required = false, defaultValue = "0") int page, Model m) {
        PeopleService.Filter f = new PeopleService.Filter(q, role, dept, year, section, status);
        m.addAttribute("result", people.search(f, page, PAGE_SIZE));
        m.addAttribute("q", q); m.addAttribute("role", role); m.addAttribute("dept", dept); m.addAttribute("year", year);
        m.addAttribute("section", section); m.addAttribute("status", status); m.addAttribute("page", page);
        m.addAttribute("roles", Role.values());
        m.addAttribute("departments", departments.findAllByOrderByCodeAsc());
        m.addAttribute("admissionYear", people.academicStartYear());
        return "admin/users";
    }

    // ------------------------------------------------------------ add

    @PostMapping("/users/student")
    public String addStudent(@RequestParam String name, @RequestParam String email, @RequestParam String register,
                             @RequestParam Integer admissionYear, @RequestParam String branch, @RequestParam String section,
                             @RequestParam(required = false) String phone, Principal p, RedirectAttributes ra) {
        return exec(ra, "/admin/users", "Student saved.", () ->
                people.saveStudent(new PeopleService.StudentData(name, register, email, admissionYear, branch, section, phone, null), p.getName()));
    }

    @PostMapping("/users/staff")
    public String addStaff(@RequestParam String name, @RequestParam String email, @RequestParam(required = false) String phone,
                           @RequestParam String branch, @RequestParam(defaultValue = "TUTOR") String role,
                           @RequestParam(required = false) String mentorClasses, Principal p, RedirectAttributes ra) {
        try {
            List<String> notes = people.saveStaff(new PeopleService.StaffData(name, email, phone, branch, role, mentorClasses), p.getName());
            ra.addFlashAttribute("msg", "Staff member saved." + (notes.isEmpty() ? "" : " Notes: " + String.join("; ", notes)));
        } catch (BusinessException e) {
            ra.addFlashAttribute("err", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/other")
    public String addOther(@RequestParam String name, @RequestParam String email, @RequestParam Role role,
                           @RequestParam(required = false) String passwordLogin, Principal p, RedirectAttributes ra) {
        return exec(ra, "/admin/users", "Account saved.", () -> people.saveSimple(name, email, role, passwordLogin != null, p.getName()));
    }

    // ------------------------------------------------------------ CSV upload

    @PostMapping("/users/import/{kind}")
    public String upload(@PathVariable String kind, @RequestParam MultipartFile file, Principal p, RedirectAttributes ra) {
        try {
            if (file.isEmpty()) throw new BusinessException("Choose a CSV file first.");
            PeopleService.Report rep = switch (kind) {
                case "students" -> people.importStudents(file.getInputStream(), p.getName());
                case "staff" -> people.importStaff(file.getInputStream(), p.getName());
                case "heads" -> people.importHeads(file.getInputStream(), p.getName());
                default -> throw new BusinessException("Unknown import type.");
            };
            ra.addFlashAttribute("msg", rep.counts.isEmpty() ? "Nothing was imported." : "Import finished: " + rep.counts + ".");
            if (!rep.errors.isEmpty()) ra.addFlashAttribute("importErrors", rep.errors);
        } catch (BusinessException e) {
            ra.addFlashAttribute("err", e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("err", "Import failed: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ------------------------------------------------------------ edit one person

    @GetMapping("/users/{id}")
    @Transactional(readOnly = true)
    public String edit(@PathVariable Long id, Model m) {
        UserAccount u = users.findById(id).orElseThrow(() -> new BusinessException("User not found."));
        m.addAttribute("u", u);
        m.addAttribute("departments", departments.findAllByOrderByCodeAsc());
        m.addAttribute("defaultPassword", people.defaultPasswordFor(u.getRole()));
        StudentProfile sp = students.findByUser(u).orElse(null);
        FacultyProfile fp = faculty.findByUser(u).orElse(null);
        m.addAttribute("sp", sp);
        m.addAttribute("fp", fp);
        if (sp != null) {
            m.addAttribute("classes", classes.findAllOrdered());
            m.addAttribute("admissionYear", people.admissionYearOf(sp.getClassSection().getAcademicYear()));
        }
        if (fp != null) {
            Set<Long> mine = new HashSet<>(tutors.activeClassIdsForFaculty(fp.getId(), LocalDate.now(clock)));
            m.addAttribute("mentoring", mine);
            m.addAttribute("deptClasses", classes.findAllOrdered().stream()
                    .filter(c -> c.getDepartment().getId().equals(fp.getDepartment().getId())).toList());
            m.addAttribute("isHod", hods.findByFacultyAndActiveTrue(fp).isPresent());
        }
        return "admin/user-edit";
    }

    @PostMapping("/users/{id}/student")
    public String saveStudent(@PathVariable Long id, @RequestParam String name, @RequestParam String email, @RequestParam String roll,
                              @RequestParam Long classId, @RequestParam(required = false) String phone,
                              @RequestParam(required = false) String enabled, Principal p, RedirectAttributes ra) {
        return exec(ra, "/admin/users/" + id, "Saved.", () -> people.updateStudent(id, name, email, roll, classId, phone, enabled != null, p.getName()));
    }

    @PostMapping("/users/{id}/staff")
    public String saveStaff(@PathVariable Long id, @RequestParam String name, @RequestParam String email, @RequestParam(required = false) String phone,
                            @RequestParam Long deptId, @RequestParam String role, @RequestParam(required = false) List<Long> mentor,
                            @RequestParam(required = false) String enabled, Principal p, RedirectAttributes ra) {
        try {
            List<String> notes = people.updateStaff(id, name, email, phone, deptId, role, mentor == null ? Set.of() : new HashSet<>(mentor), enabled != null, p.getName());
            ra.addFlashAttribute("msg", "Saved." + (notes.isEmpty() ? "" : " Notes: " + String.join("; ", notes)));
        } catch (BusinessException e) {
            ra.addFlashAttribute("err", e.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/basic")
    public String saveBasic(@PathVariable Long id, @RequestParam String name, @RequestParam String email,
                            @RequestParam(required = false) String enabled, Principal p, RedirectAttributes ra) {
        return exec(ra, "/admin/users/" + id, "Saved.", () -> people.updateSimple(id, name, email, enabled != null, p.getName()));
    }

    @PostMapping("/users/{id}/password")
    public String password(@PathVariable Long id, @RequestParam(required = false) String custom, Principal p, RedirectAttributes ra) {
        try {
            String pw = people.resetPassword(id, custom, p.getName());
            ra.addFlashAttribute("msg", "Password set to \"" + pw + "\". The person must change it at next login.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("err", e.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/login-mode")
    public String loginMode(@PathVariable Long id, @RequestParam(required = false) String passwordLogin, Principal p, RedirectAttributes ra) {
        try {
            String pw = people.setLoginMode(id, passwordLogin != null, p.getName());
            ra.addFlashAttribute("msg", passwordLogin != null
                    ? (pw == null ? "No change." : "Password sign-in enabled. Temporary password: \"" + pw + "\" (must be changed at first login).")
                    : "This account now signs in with Google only.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("err", e.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    @PostMapping("/users/{id}/toggle")
    public String toggle(@PathVariable Long id, @RequestHeader(value = "Referer", required = false) String ref, Principal p, RedirectAttributes ra) {
        return exec(ra, "/admin/users", "Account status updated.", () -> people.toggleEnabled(id, p.getName()));
    }

    // ------------------------------------------------------------ delete people

    @PostMapping("/users/delete")
    public String deleteSelected(@RequestParam(required = false) List<Long> ids, @RequestParam String confirm, Principal p, RedirectAttributes ra) {
        if (ids == null || ids.isEmpty()) { ra.addFlashAttribute("err", "Tick at least one person."); return "redirect:/admin/users"; }
        if (!confirmed(confirm)) { ra.addFlashAttribute("err", "Nothing deleted: type DELETE to confirm."); return "redirect:/admin/users"; }
        return reportDelete(purge.deletePeople(ids, p.getName()), ra, "/admin/users");
    }

    @SuppressWarnings("unchecked")
    private String reportDelete(Map<String, Object> r, RedirectAttributes ra, String back) {
        List<String> skipped = (List<String>) r.get("skipped");
        ra.addFlashAttribute("msg", r.get("deleted") + " account(s) permanently deleted.");
        if (!skipped.isEmpty()) ra.addFlashAttribute("importErrors", skipped);
        return "redirect:" + back;
    }

    // ------------------------------------------------------------ data clean-up

    @GetMapping("/cleanup")
    public String cleanup(Model m) {
        m.addAttribute("departments", departments.findAllByOrderByCodeAsc());
        m.addAttribute("classes", classes.findAllOrdered());
        return "admin/cleanup";
    }

    @PostMapping("/cleanup")
    public String runCleanup(@RequestParam String scope, @RequestParam(required = false) String person, @RequestParam(required = false) String dept,
                             @RequestParam(required = false) Long classId,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             @RequestParam(required = false) String requests, @RequestParam(required = false) String scans,
                             @RequestParam(required = false) String notifications, @RequestParam(required = false) String auditLog,
                             @RequestParam String mode, @RequestParam(required = false) String confirm, Principal pr, Model m) {
        m.addAttribute("departments", departments.findAllByOrderByCodeAsc());
        m.addAttribute("classes", classes.findAllOrdered());
        try {
            boolean dry = !"delete".equals(mode);
            if (!dry && !confirmed(confirm)) throw new BusinessException("Nothing deleted: type DELETE in the confirmation box.");
            var spec = new PurgeService.Spec(scope, person, dept, classId, from, to, requests != null, scans != null, notifications != null, auditLog != null);
            if (!spec.requests() && !spec.scans() && !spec.notifications() && !spec.auditLog()) throw new BusinessException("Tick at least one kind of data.");
            Map<String, Integer> res = purge.purge(spec, dry, pr.getName());
            m.addAttribute("result", res);
            m.addAttribute("dry", dry);
        } catch (BusinessException e) {
            m.addAttribute("err", e.getMessage());
        }
        return "admin/cleanup";
    }

    @PostMapping("/cleanup/people")
    public String wipeRole(@RequestParam Role role, @RequestParam String confirm, Principal p, RedirectAttributes ra) {
        if (!confirmed(confirm)) { ra.addFlashAttribute("err", "Nothing deleted: type DELETE to confirm."); return "redirect:/admin/cleanup"; }
        try {
            return reportDelete(purge.deleteAllOfRole(role, p.getName()), ra, "/admin/cleanup");
        } catch (BusinessException e) {
            ra.addFlashAttribute("err", e.getMessage());
            return "redirect:/admin/cleanup";
        }
    }

    // ------------------------------------------------------------ format guide

    @GetMapping("/formats")
    public String formats(Model m) {
        m.addAttribute("departments", departments.findAllByOrderByCodeAsc());
        m.addAttribute("admissionYear", people.academicStartYear());
        return "admin/formats";
    }
}
