package com.psgitech.outpass.service;

import com.psgitech.outpass.domain.*;
import com.psgitech.outpass.repo.Repos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

/** Structure (departments, classes, gates) and tutor / HOD assignments. People live in PeopleService. */
@Service
@RequiredArgsConstructor
public class AdminService {
    private final Repos.Departments departments;
    private final Repos.ClassSections classes;
    private final Repos.Faculty faculty;
    private final Repos.TutorAssignments tutors;
    private final Repos.HodAssignments hods;
    private final Repos.Gates gates;
    private final AuditService audit;
    private final Clock clock;

    // ---------------------------------------------------------------- structure

    @Transactional(noRollbackFor = BusinessException.class)
    public void createDepartment(String code, String name, String actor) {
        requireText(code, "Code"); requireText(name, "Name");
        if (departments.findByCodeIgnoreCase(code.trim()).isPresent()) throw new BusinessException("Department already exists.");
        Department d = new Department();
        d.setCode(code.trim().toUpperCase(Locale.ROOT));
        d.setName(name.trim());
        departments.save(d);
        audit.log(actor, "DEPARTMENT_CREATED", "Department", d.getId(), d.getCode());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void createClass(Long deptId, int year, String section, int semester, String actor) {
        Department d = departments.findById(deptId).orElseThrow(() -> new BusinessException("Select a department."));
        requireText(section, "Section");
        if (year < 1 || year > 6) throw new BusinessException("Academic year must be 1-6.");
        if (classes.find(d, year, section.trim().toUpperCase(Locale.ROOT)).isPresent()) throw new BusinessException("Class already exists.");
        ClassSection c = new ClassSection();
        c.setDepartment(d); c.setAcademicYear(year); c.setSection(section.trim().toUpperCase(Locale.ROOT)); c.setSemester(semester);
        classes.save(c);
        audit.log(actor, "CLASS_CREATED", "ClassSection", c.getId(), c.getLabel());
    }

    /** Finds the class or creates it (used by imports). */
    @Transactional
    public ClassSection ensureClass(Department d, int year, String section, int semester) {
        String sec = section.trim().toUpperCase(Locale.ROOT);
        return classes.find(d, year, sec).orElseGet(() -> {
            ClassSection c = new ClassSection();
            c.setDepartment(d); c.setAcademicYear(year); c.setSection(sec); c.setSemester(semester);
            return classes.save(c);
        });
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void createGate(String name, String actor) {
        requireText(name, "Gate name");
        Gate g = new Gate();
        g.setName(name.trim());
        gates.save(g);
        audit.log(actor, "GATE_CREATED", "Gate", g.getId(), g.getName());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void toggleGate(Long id, String actor) {
        Gate g = gates.findById(id).orElseThrow(() -> new BusinessException("Gate not found."));
        g.setActive(!g.isActive());
        audit.log(actor, "GATE_TOGGLED", "Gate", g.getId(), g.getName() + " active=" + g.isActive());
    }

    @Transactional
    public Gate ensureGate(String name) {
        return gates.findAllByOrderByName().stream().filter(g -> g.getName().equalsIgnoreCase(name)).findFirst().orElseGet(() -> {
            Gate g = new Gate(); g.setName(name); return gates.save(g);
        });
    }

    // ---------------------------------------------------------------- tutor assignments

    @Transactional(noRollbackFor = BusinessException.class)
    public void assignTutor(Long classId, Long facultyId, String actor) {
        ClassSection cs = classes.findById(classId).orElseThrow(() -> new BusinessException("Select a class."));
        FacultyProfile fp = faculty.findById(facultyId).orElseThrow(() -> new BusinessException("Select a faculty member."));
        if (!fp.getDepartment().getId().equals(cs.getDepartment().getId()))
            throw new BusinessException("Tutors must belong to the same department as the class.");
        LocalDate today = LocalDate.now(clock);
        List<TutorAssignment> active = tutors.activeForClass(classId, today);
        if (active.size() >= 2) throw new BusinessException("This class already has two active tutors. End one assignment first.");
        if (active.stream().anyMatch(t -> t.getFaculty().getId().equals(facultyId)))
            throw new BusinessException("This faculty member is already a tutor of the class.");
        TutorAssignment t = new TutorAssignment();
        t.setClassSection(cs); t.setFaculty(fp); t.setEffectiveFrom(today);
        tutors.save(t);
        audit.log(actor, "TUTOR_ASSIGNED", "ClassSection", classId, fp.getUser().getFullName() + " -> " + cs.getLabel());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void endTutor(Long assignmentId, String actor) {
        TutorAssignment t = tutors.findById(assignmentId).orElseThrow(() -> new BusinessException("Assignment not found."));
        t.setEffectiveTo(LocalDate.now(clock).minusDays(1));
        audit.log(actor, "TUTOR_UNASSIGNED", "ClassSection", t.getClassSection().getId(), t.getFaculty().getUser().getFullName());
    }

    /**
     * Makes the given classes exactly the classes this faculty member mentors. Problems (class already has two
     * tutors, other department) are returned as messages instead of failing the whole operation.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public List<String> setMentorClasses(Long facultyId, Set<Long> wanted, String actor) {
        FacultyProfile fp = faculty.findById(facultyId).orElseThrow(() -> new BusinessException("Faculty not found."));
        LocalDate today = LocalDate.now(clock);
        List<String> notes = new ArrayList<>();
        Set<Long> current = new HashSet<>(tutors.activeClassIdsForFaculty(fp.getId(), today));
        for (Long cid : current) {
            if (wanted.contains(cid)) continue;
            for (TutorAssignment t : tutors.activeForClass(cid, today))
                if (t.getFaculty().getId().equals(fp.getId())) t.setEffectiveTo(today.minusDays(1));
            audit.log(actor, "TUTOR_UNASSIGNED", "ClassSection", cid, fp.getUser().getFullName());
        }
        for (Long cid : wanted) {
            if (current.contains(cid)) continue;
            ClassSection cs = classes.findById(cid).orElse(null);
            if (cs == null) { notes.add("Unknown class id " + cid); continue; }
            if (!cs.getDepartment().getId().equals(fp.getDepartment().getId())) {
                notes.add(cs.getLabel() + ": belongs to another department"); continue;
            }
            if (tutors.activeForClass(cid, today).size() >= 2) {
                notes.add(cs.getLabel() + ": already has two tutors"); continue;
            }
            TutorAssignment t = new TutorAssignment();
            t.setClassSection(cs); t.setFaculty(fp); t.setEffectiveFrom(today);
            tutors.save(t);
            audit.log(actor, "TUTOR_ASSIGNED", "ClassSection", cid, fp.getUser().getFullName() + " -> " + cs.getLabel());
        }
        return notes;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void setHod(Long facultyId, String actor) {
        FacultyProfile fp = faculty.findById(facultyId).orElseThrow(() -> new BusinessException("Select a faculty member."));
        Department d = fp.getDepartment();
        hods.findByDepartmentAndActiveTrue(d).ifPresent(old -> {
            if (old.getFaculty().getId().equals(fp.getId())) return;
            old.setActive(false);
            hods.flush();
            UserAccount ou = old.getFaculty().getUser();
            if (ou.getRole() == Role.HOD) ou.setRole(Role.TUTOR);
        });
        if (hods.findByDepartmentAndActiveTrue(d).isEmpty()) {
            HodAssignment h = new HodAssignment();
            h.setDepartment(d); h.setFaculty(fp);
            hods.save(h);
        }
        fp.getUser().setRole(Role.HOD);
        audit.log(actor, "HOD_ASSIGNED", "Department", d.getId(), fp.getUser().getFullName());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void clearHod(Long facultyId, String actor) {
        FacultyProfile fp = faculty.findById(facultyId).orElseThrow(() -> new BusinessException("Faculty not found."));
        hods.findByFacultyAndActiveTrue(fp).ifPresent(h -> h.setActive(false));
        if (fp.getUser().getRole() == Role.HOD) fp.getUser().setRole(Role.TUTOR);
        audit.log(actor, "HOD_REMOVED", "Department", fp.getDepartment().getId(), fp.getUser().getFullName());
    }

    // ---------------------------------------------------------------- helpers

    /** Minimal RFC-4180 style line parser (handles quoted fields and escaped quotes). */
    static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else quoted = false;
                } else cur.append(ch);
            } else if (ch == '"') quoted = true;
            else if (ch == ',') { out.add(cur.toString()); cur.setLength(0); }
            else cur.append(ch);
        }
        out.add(cur.toString());
        return out;
    }

    static void requireText(String v, String field) {
        if (v == null || v.isBlank()) throw new BusinessException(field + " is required.");
    }
}
