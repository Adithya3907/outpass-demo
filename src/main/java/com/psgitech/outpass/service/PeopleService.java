package com.psgitech.outpass.service;

import com.psgitech.outpass.config.OutpassProperties;
import com.psgitech.outpass.domain.*;
import com.psgitech.outpass.repo.Repos;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Everything about people: create / update / search students, staff, heads, security and admins,
 * and the CSV imports that use the same rules (so the CSV, the forms and the exports always agree).
 */
@Service
@RequiredArgsConstructor
public class PeopleService {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern MENTOR = Pattern.compile("^(\\d{1,4})\\s*-\\s*([A-Za-z0-9]{1,10})$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9][0-9 \\-]{8,18}[0-9]$");

    private final Repos.Users users;
    private final Repos.Departments departments;
    private final Repos.ClassSections classes;
    private final Repos.Students students;
    private final Repos.Faculty faculty;
    private final Repos.TutorAssignments tutors;
    private final Repos.HodAssignments hods;
    private final PasswordEncoder encoder;
    private final OutpassProperties props;
    private final AdminService admin;
    private final AuditService audit;
    private final Clock clock;

    @PersistenceContext
    private EntityManager em;

    private final Map<String, String> hashCache = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ records

    public record StudentData(String name, String register, String email, Integer admissionYear, String branch,
                              String section, String phone, Boolean active) {}
    public record StaffData(String name, String email, String phone, String branch, String role, String mentorClasses) {}
    public record Filter(String q, String role, String dept, Integer year, String section, String status) {}
    public record Row(UserAccount user, String branch, String klass, String roll, String phone) {}
    public record PageResult(List<Row> rows, long total, int page, int pages) {}

    public static class Report {
        public final Map<String, Integer> counts = new LinkedHashMap<>();
        public final List<String> errors = new ArrayList<>();
        public void add(String k) { counts.merge(k, 1, Integer::sum); }
        public void err(String m) { if (errors.size() < 150) errors.add(m); }
    }

    private enum Outcome { CREATED, UPDATED }

    // ------------------------------------------------------------------ dates / defaults

    /** The academic year starts in June. */
    public int academicStartYear() {
        LocalDate t = LocalDate.now(clock);
        return t.getMonthValue() >= 6 ? t.getYear() : t.getYear() - 1;
    }

    public int classYearOf(int admissionYear) { return academicStartYear() - admissionYear + 1; }
    public int admissionYearOf(int classYear) { return academicStartYear() - classYear + 1; }

    public int semesterFor(int classYear) {
        int m = LocalDate.now(clock).getMonthValue();
        boolean odd = m >= 6 && m <= 11;
        return Math.max(1, Math.min(odd ? classYear * 2 - 1 : classYear * 2, 12));
    }

    private static int maxYear(Department d, String section) {
        if ("STRUCT".equalsIgnoreCase(d.getCode())) return 2;
        if ("SW".equalsIgnoreCase(section)) return 5;
        return 4;
    }

    public String defaultPasswordFor(Role role) {
        return role == Role.STUDENT ? props.getStudentDefaultPassword() : props.getStaffDefaultPassword();
    }

    private String hashFor(String password) {
        return hashCache.computeIfAbsent(password, encoder::encode);
    }

    // ------------------------------------------------------------------ students

    @Transactional(noRollbackFor = BusinessException.class)
    public void saveStudent(StudentData d, String actor) {
        doStudent(new Ctx(), d, actor);
    }

    private Outcome doStudent(Ctx ctx, StudentData d, String actor) {
        String name = req(d.name(), "Name");
        String email = email(d.email());
        String roll = req(d.register(), "Register number").toUpperCase(Locale.ROOT);
        if (d.admissionYear() == null) throw new BusinessException("Year of admission is required.");
        String section = req(d.section(), "Section").toUpperCase(Locale.ROOT);
        Department dept = ctx.dept(d.branch());
        if (dept == null) throw new BusinessException("Unknown branch '" + d.branch() + "'. Use a branch code from the list on the Formats page.");
        int year = classYearOf(d.admissionYear());
        if (year < 1) throw new BusinessException("Year of admission " + d.admissionYear() + " is in the future.");
        if (year > 8) throw new BusinessException("Year of admission " + d.admissionYear() + " is too old.");
        String phone = optPhone(d.phone());
        ClassSection cs = ctx.cls(dept, year, section);
        boolean active = d.active() != null ? d.active() : year <= maxYear(dept, section);

        UserAccount u = users.findByEmailIgnoreCase(email).orElse(null);
        if (u == null) {
            var owner = students.findByRollNumberIgnoreCase(roll);
            if (owner.isPresent()) throw new BusinessException("Register number " + roll + " already belongs to " + owner.get().getUser().getEmail()
                    + ". To change a student's e-mail, open the student under People and use Edit.");
            u = new UserAccount();
            u.setEmail(email); u.setFullName(name); u.setRole(Role.STUDENT);
            u.setPasswordHash(hashFor(props.getStudentDefaultPassword()));     // unused: students sign in with Google
            u.setMustChangePassword(false);
            u.setEnabled(active);
            u = users.save(u);
            StudentProfile sp = new StudentProfile();
            sp.setUser(u); sp.setRollNumber(roll); sp.setClassSection(cs); sp.setPhone(phone);
            students.save(sp);
            audit.log(actor, "STUDENT_CREATED", "UserAccount", u.getId(), roll);
            return Outcome.CREATED;
        }
        StudentProfile sp = students.findByUser(u).orElseThrow(() -> new BusinessException(email + " belongs to a non-student account."));
        Optional<StudentProfile> other = students.findByRollNumberIgnoreCase(roll);
        if (other.isPresent() && !other.get().getId().equals(sp.getId()))
            throw new BusinessException("Register number " + roll + " already belongs to another student.");
        u.setFullName(name);
        sp.setRollNumber(roll);
        sp.setClassSection(cs);
        if (phone != null) sp.setPhone(phone);
        if (d.active() != null) u.setEnabled(d.active());
        audit.log(actor, "STUDENT_UPDATED", "UserAccount", u.getId(), roll);
        return Outcome.UPDATED;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void updateStudent(Long userId, String name, String email, String roll, Long classId, String phone, boolean enabled, String actor) {
        UserAccount u = users.findById(userId).orElseThrow(() -> new BusinessException("User not found."));
        StudentProfile sp = students.findByUser(u).orElseThrow(() -> new BusinessException("Not a student account."));
        String em = email(email);
        users.findByEmailIgnoreCase(em).filter(x -> !x.getId().equals(u.getId())).ifPresent(x -> { throw new BusinessException("E-mail already in use."); });
        String rn = req(roll, "Register number").toUpperCase(Locale.ROOT);
        students.findByRollNumberIgnoreCase(rn).filter(x -> !x.getId().equals(sp.getId())).ifPresent(x -> { throw new BusinessException("Register number already in use."); });
        ClassSection cs = classes.findById(classId).orElseThrow(() -> new BusinessException("Select a class."));
        u.setFullName(req(name, "Name")); u.setEmail(em); u.setEnabled(enabled);
        sp.setRollNumber(rn); sp.setClassSection(cs);
        String ph = optPhone(phone);
        sp.setPhone(ph);
        audit.log(actor, "STUDENT_EDITED", "UserAccount", u.getId(), rn);
    }

    // ------------------------------------------------------------------ staff

    @Transactional(noRollbackFor = BusinessException.class)
    public List<String> saveStaff(StaffData d, String actor) {
        List<String> notes = new ArrayList<>();
        doStaff(new Ctx(), d, actor, notes);
        return notes;
    }

    private Outcome doStaff(Ctx ctx, StaffData d, String actor, List<String> notes) {
        String name = req(d.name(), "Name");
        String email = email(d.email());
        String phone = optPhone(d.phone());
        String roleText = d.role() == null || d.role().isBlank() ? "" : d.role().trim().toUpperCase(Locale.ROOT);
        if (!roleText.isEmpty() && !roleText.equals("TUTOR") && !roleText.equals("HOD"))
            throw new BusinessException("Role must be TUTOR or HOD (got '" + d.role() + "').");
        Department dept = d.branch() == null || d.branch().isBlank() ? null : ctx.dept(d.branch());
        if (d.branch() != null && !d.branch().isBlank() && dept == null)
            throw new BusinessException("Unknown branch '" + d.branch() + "'.");

        UserAccount u = users.findByEmailIgnoreCase(email).orElse(null);
        FacultyProfile fp;
        Outcome out;
        if (u == null) {
            u = new UserAccount();
            u.setEmail(email); u.setFullName(name); u.setRole(Role.TUTOR);
            u.setPasswordHash(hashFor(props.getStaffDefaultPassword()));       // unused: staff sign in with Google
            u.setMustChangePassword(false);
            u = users.save(u);
            fp = new FacultyProfile();
            fp.setUser(u);
            fp.setDepartment(dept != null ? dept : ctx.unassigned());
            fp.setPhone(phone);
            fp = faculty.save(fp);
            out = Outcome.CREATED;
            audit.log(actor, "STAFF_CREATED", "UserAccount", u.getId(), email);
        } else {
            fp = faculty.findByUser(u).orElseThrow(() -> new BusinessException(email + " belongs to a non-faculty account."));
            u.setFullName(name);
            if (phone != null) fp.setPhone(phone);
            if (dept != null) fp.setDepartment(dept);
            out = Outcome.UPDATED;
            audit.log(actor, "STAFF_UPDATED", "UserAccount", u.getId(), email);
        }
        if (roleText.equals("HOD")) admin.setHod(fp.getId(), actor);
        else if (roleText.equals("TUTOR") && u.getRole() == Role.HOD) admin.clearHod(fp.getId(), actor);

        if (d.mentorClasses() != null && !d.mentorClasses().isBlank()) {
            Set<Long> ids = new LinkedHashSet<>();
            for (String tok : d.mentorClasses().split("[;|]")) {
                String t = tok.trim();
                if (t.isEmpty()) continue;
                java.util.regex.Matcher m = MENTOR.matcher(t);
                if (!m.matches()) { notes.add(email + ": bad class '" + t + "' (use year-section, e.g. 2-A)"); continue; }
                int y = Integer.parseInt(m.group(1));
                if (y > 1000) y = classYearOf(y);
                if (y < 1 || y > 8) { notes.add(email + ": bad class year in '" + t + "'"); continue; }
                ids.add(ctx.cls(fp.getDepartment(), y, m.group(2)).getId());
            }
            notes.addAll(admin.setMentorClasses(fp.getId(), ids, actor).stream().map(x -> email + ": " + x).toList());
        }
        return out;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public List<String> updateStaff(Long userId, String name, String email, String phone, Long deptId, String role,
                                    Set<Long> mentorIds, boolean enabled, String actor) {
        UserAccount u = users.findById(userId).orElseThrow(() -> new BusinessException("User not found."));
        FacultyProfile fp = faculty.findByUser(u).orElseThrow(() -> new BusinessException("Not a faculty account."));
        String em = email(email);
        users.findByEmailIgnoreCase(em).filter(x -> !x.getId().equals(u.getId())).ifPresent(x -> { throw new BusinessException("E-mail already in use."); });
        Department d = departments.findById(deptId).orElseThrow(() -> new BusinessException("Select a department."));
        u.setFullName(req(name, "Name")); u.setEmail(em); u.setEnabled(enabled);
        fp.setPhone(optPhone(phone));
        fp.setDepartment(d);
        List<String> notes = new ArrayList<>();
        if ("HOD".equalsIgnoreCase(role)) admin.setHod(fp.getId(), actor);
        else if (u.getRole() == Role.HOD) admin.clearHod(fp.getId(), actor);
        notes.addAll(admin.setMentorClasses(fp.getId(), mentorIds == null ? Set.of() : mentorIds, actor));
        audit.log(actor, "STAFF_EDITED", "UserAccount", u.getId(), em);
        return notes;
    }

    // ------------------------------------------------------------------ other accounts (head / security / admin)

    @Transactional(noRollbackFor = BusinessException.class)
    public UserAccount saveSimple(String name, String email, Role role, boolean passwordLogin, String actor) {
        if (role != Role.SECURITY && role != Role.ADMIN && role != Role.HEAD) throw new BusinessException("Invalid role.");
        String em = email(email);
        UserAccount u = users.findByEmailIgnoreCase(em).orElse(null);
        if (u != null) {
            if (u.getRole() != role) throw new BusinessException(em + " already exists with role " + u.getRole() + ".");
            u.setFullName(req(name, "Name"));
            return u;
        }
        u = new UserAccount();
        u.setEmail(em); u.setFullName(req(name, "Name")); u.setRole(role);
        u.setPasswordHash(hashFor(props.getStaffDefaultPassword()));
        u.setLocalLogin(passwordLogin);                    // false = Google sign-in only
        u.setMustChangePassword(passwordLogin);
        u = users.save(u);
        audit.log(actor, role + "_CREATED", "UserAccount", u.getId(), em);
        return u;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void updateSimple(Long userId, String name, String email, boolean enabled, String actor) {
        UserAccount u = users.findById(userId).orElseThrow(() -> new BusinessException("User not found."));
        String em = email(email);
        users.findByEmailIgnoreCase(em).filter(x -> !x.getId().equals(u.getId())).ifPresent(x -> { throw new BusinessException("E-mail already in use."); });
        if (u.getEmail().equalsIgnoreCase(actor) && !enabled) throw new BusinessException("You cannot disable your own account.");
        u.setFullName(req(name, "Name")); u.setEmail(em); u.setEnabled(enabled);
        audit.log(actor, "USER_EDITED", "UserAccount", u.getId(), em);
    }

    // ------------------------------------------------------------------ passwords / status

    /** Resets to the role's default password (or a password the admin types) and forces a change at next login. */
    @Transactional(noRollbackFor = BusinessException.class)
    public String resetPassword(Long userId, String custom, String actor) {
        UserAccount u = users.findById(userId).orElseThrow(() -> new BusinessException("User not found."));
        if (!u.isLocalLogin()) throw new BusinessException("This account signs in with Google and has no password. Enable password sign-in first if you need one.");
        boolean hasCustom = custom != null && !custom.isBlank();
        if (hasCustom && custom.length() < 6) throw new BusinessException("Password must be at least 6 characters.");
        String pw = hasCustom ? custom : defaultPasswordFor(u.getRole());
        u.setPasswordHash(hasCustom ? encoder.encode(pw) : hashFor(pw));
        u.setMustChangePassword(true);
        audit.log(actor, "PASSWORD_RESET", "UserAccount", u.getId(), u.getEmail());
        return pw;
    }

    /** Switches an account between Google-only sign-in and password sign-in (for people without a Google account). */
    @Transactional(noRollbackFor = BusinessException.class)
    public String setLoginMode(Long userId, boolean passwordLogin, String actor) {
        UserAccount u = users.findById(userId).orElseThrow(() -> new BusinessException("User not found."));
        if (u.isLocalLogin() == passwordLogin) return null;
        u.setLocalLogin(passwordLogin);
        String pw = null;
        if (passwordLogin) {
            pw = defaultPasswordFor(u.getRole());
            u.setPasswordHash(hashFor(pw));
            u.setMustChangePassword(true);
        } else {
            u.setMustChangePassword(false);
        }
        audit.log(actor, passwordLogin ? "PASSWORD_LOGIN_ENABLED" : "GOOGLE_ONLY", "UserAccount", u.getId(), u.getEmail());
        return pw;
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void toggleEnabled(Long userId, String actor) {
        UserAccount u = users.findById(userId).orElseThrow(() -> new BusinessException("User not found."));
        if (u.getEmail().equalsIgnoreCase(actor)) throw new BusinessException("You cannot disable your own account.");
        u.setEnabled(!u.isEnabled());
        audit.log(actor, u.isEnabled() ? "USER_ENABLED" : "USER_DISABLED", "UserAccount", u.getId(), u.getEmail());
    }

    // ------------------------------------------------------------------ search

    private String peopleWhere(Filter f, Map<String, Object> p) {
        StringBuilder w = new StringBuilder(" where 1=1");
        if (notBlank(f.q())) {
            w.append(" and (lower(u.fullName) like :q or lower(u.email) like :q or exists (select 1 from StudentProfile s0 where s0.user = u and lower(s0.rollNumber) like :q))");
            p.put("q", "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%");
        }
        if (notBlank(f.role())) { w.append(" and u.role = :role"); p.put("role", Role.valueOf(f.role())); }
        if (notBlank(f.dept())) {
            w.append(" and (exists (select 1 from StudentProfile s1 where s1.user = u and s1.classSection.department.code = :dept)")
             .append(" or exists (select 1 from FacultyProfile f1 where f1.user = u and f1.department.code = :dept))");
            p.put("dept", f.dept().trim().toUpperCase(Locale.ROOT));
        }
        if (f.year() != null) {
            w.append(" and exists (select 1 from StudentProfile s2 where s2.user = u and s2.classSection.academicYear = :year)");
            p.put("year", f.year());
        }
        if (notBlank(f.section())) {
            w.append(" and exists (select 1 from StudentProfile s3 where s3.user = u and s3.classSection.section = :sec)");
            p.put("sec", f.section().trim().toUpperCase(Locale.ROOT));
        }
        if ("ACTIVE".equals(f.status())) { w.append(" and u.enabled = true"); }
        if ("DISABLED".equals(f.status())) { w.append(" and u.enabled = false"); }
        return w.toString();
    }

    @Transactional(readOnly = true)
    public PageResult search(Filter f, int page, int size) {
        Map<String, Object> p = new HashMap<>();
        String where = peopleWhere(f, p);
        TypedQuery<Long> cq = em.createQuery("select count(u) from UserAccount u" + where, Long.class);
        p.forEach(cq::setParameter);
        long total = cq.getSingleResult();
        TypedQuery<UserAccount> q = em.createQuery("select u from UserAccount u" + where + " order by u.role, u.fullName", UserAccount.class);
        p.forEach(q::setParameter);
        q.setFirstResult(Math.max(0, page) * size).setMaxResults(size);
        List<UserAccount> list = q.getResultList();

        Map<Long, StudentProfile> sm = new HashMap<>();
        Map<Long, FacultyProfile> fm = new HashMap<>();
        if (!list.isEmpty()) {
            em.createQuery("select s from StudentProfile s join fetch s.classSection c join fetch c.department where s.user in :us", StudentProfile.class)
                    .setParameter("us", list).getResultList().forEach(s -> sm.put(s.getUser().getId(), s));
            em.createQuery("select f from FacultyProfile f join fetch f.department where f.user in :us", FacultyProfile.class)
                    .setParameter("us", list).getResultList().forEach(x -> fm.put(x.getUser().getId(), x));
        }
        List<Row> rows = new ArrayList<>();
        for (UserAccount u : list) {
            StudentProfile s = sm.get(u.getId());
            FacultyProfile fp = fm.get(u.getId());
            if (s != null) rows.add(new Row(u, s.getClassSection().getDepartment().getCode(), s.getClassSection().getLabel(), s.getRollNumber(), s.getPhone()));
            else if (fp != null) rows.add(new Row(u, fp.getDepartment().getCode(), "", "", fp.getPhone()));
            else rows.add(new Row(u, "", "", "", ""));
        }
        int pages = (int) Math.max(1, (total + size - 1) / size);
        return new PageResult(rows, total, page, pages);
    }

    /** Students for export (no paging). */
    @Transactional(readOnly = true)
    public List<StudentProfile> studentsFor(Filter f) {
        Map<String, Object> p = new HashMap<>();
        StringBuilder w = new StringBuilder(" where 1=1");
        if (notBlank(f.q())) {
            w.append(" and (lower(u.fullName) like :q or lower(u.email) like :q or lower(s.rollNumber) like :q)");
            p.put("q", "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%");
        }
        if (notBlank(f.dept())) { w.append(" and d.code = :dept"); p.put("dept", f.dept().trim().toUpperCase(Locale.ROOT)); }
        if (f.year() != null) { w.append(" and c.academicYear = :year"); p.put("year", f.year()); }
        if (notBlank(f.section())) { w.append(" and c.section = :sec"); p.put("sec", f.section().trim().toUpperCase(Locale.ROOT)); }
        if ("ACTIVE".equals(f.status())) w.append(" and u.enabled = true");
        if ("DISABLED".equals(f.status())) w.append(" and u.enabled = false");
        TypedQuery<StudentProfile> q = em.createQuery("select s from StudentProfile s join fetch s.user u join fetch s.classSection c join fetch c.department d"
                + w + " order by d.code, c.academicYear, c.section, s.rollNumber", StudentProfile.class);
        p.forEach(q::setParameter);
        return q.getResultList();
    }

    /** Faculty for export (no paging). */
    @Transactional(readOnly = true)
    public List<FacultyProfile> facultyFor(Filter f) {
        Map<String, Object> p = new HashMap<>();
        StringBuilder w = new StringBuilder(" where 1=1");
        if (notBlank(f.q())) {
            w.append(" and (lower(u.fullName) like :q or lower(u.email) like :q)");
            p.put("q", "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%");
        }
        if (notBlank(f.dept())) { w.append(" and d.code = :dept"); p.put("dept", f.dept().trim().toUpperCase(Locale.ROOT)); }
        if (notBlank(f.role())) { w.append(" and u.role = :role"); p.put("role", Role.valueOf(f.role())); }
        if ("ACTIVE".equals(f.status())) w.append(" and u.enabled = true");
        if ("DISABLED".equals(f.status())) w.append(" and u.enabled = false");
        TypedQuery<FacultyProfile> q = em.createQuery("select f from FacultyProfile f join fetch f.user u join fetch f.department d"
                + w + " order by d.code, u.fullName", FacultyProfile.class);
        p.forEach(q::setParameter);
        return q.getResultList();
    }

    /** "2-A;3-B" style list of the classes a faculty member currently mentors. */
    @Transactional(readOnly = true)
    public String mentorClassText(FacultyProfile fp) {
        List<Long> ids = tutors.activeClassIdsForFaculty(fp.getId(), LocalDate.now(clock));
        List<String> out = new ArrayList<>();
        for (ClassSection c : classes.findAllById(ids)) out.add(c.getAcademicYear() + "-" + c.getSection());
        Collections.sort(out);
        return String.join(";", out);
    }

    // ------------------------------------------------------------------ CSV imports

    @Transactional
    public Report importStudents(InputStream in, String actor) throws IOException {
        Report rep = new Report();
        Ctx ctx = new Ctx();
        List<Map<String, String>> rows = readCsv(in, List.of("name", "register", "email", "admission", "branch", "section"));
        int n = 1;
        for (Map<String, String> r : rows) {
            n++;
            try {
                Integer adm = r.get("admission").isBlank() ? null : Integer.valueOf(r.get("admission").trim());
                Boolean active = r.getOrDefault("active", "").isBlank() ? null : !"false".equalsIgnoreCase(r.get("active").trim()) && !"0".equals(r.get("active").trim());
                Outcome o = doStudent(ctx, new StudentData(r.get("name"), r.get("register"), r.get("email"), adm, r.get("branch"),
                        r.get("section"), r.getOrDefault("phone", ""), active), actor);
                rep.add(o == Outcome.CREATED ? "students created" : "students updated");
            } catch (BusinessException | NumberFormatException e) {
                rep.err("Row " + n + " (" + r.get("email") + "): " + (e instanceof NumberFormatException ? "year of admission must be a number" : e.getMessage()));
            }
        }
        audit.log(actor, "STUDENT_IMPORT", "UserAccount", null, rep.counts + " errors=" + rep.errors.size());
        return rep;
    }

    @Transactional
    public Report importStaff(InputStream in, String actor) throws IOException {
        Report rep = new Report();
        Ctx ctx = new Ctx();
        List<Map<String, String>> rows = readCsv(in, List.of("name", "email"));
        int n = 1;
        for (Map<String, String> r : rows) {
            n++;
            try {
                List<String> notes = new ArrayList<>();
                Outcome o = doStaff(ctx, new StaffData(r.get("name"), r.get("email"), r.getOrDefault("phone", ""), r.getOrDefault("branch", ""),
                        r.getOrDefault("role", ""), r.getOrDefault("mentor", "")), actor, notes);
                rep.add(o == Outcome.CREATED ? "staff created" : "staff updated");
                notes.forEach(rep::err);
            } catch (BusinessException e) {
                rep.err("Row " + n + " (" + r.get("email") + "): " + e.getMessage());
            }
        }
        audit.log(actor, "STAFF_IMPORT", "UserAccount", null, rep.counts + " errors=" + rep.errors.size());
        return rep;
    }

    @Transactional
    public Report importHeads(InputStream in, String actor) throws IOException {
        Report rep = new Report();
        int n = 1;
        for (Map<String, String> r : readCsv(in, List.of("name", "email"))) {
            n++;
            try {
                boolean existed = users.existsByEmailIgnoreCase(r.get("email") == null ? "" : r.get("email").trim());
                saveSimple(r.get("name"), r.get("email"), Role.HEAD, false, actor);
                rep.add(existed ? "heads updated" : "heads created");
            } catch (BusinessException e) {
                rep.err("Row " + n + " (" + r.get("email") + "): " + e.getMessage());
            }
        }
        return rep;
    }

    static String canon(String h) {
        String s = h.trim().toLowerCase(Locale.ROOT).replace("\uFEFF", "").replaceAll("[\\s\\-]+", "_");
        return switch (s) {
            case "register_number", "reg_no", "regno", "register_no", "roll", "roll_number", "register" -> "register";
            case "admission_year", "year_of_admission", "batch", "admission" -> "admission";
            case "branch", "dept", "department" -> "branch";
            case "mail", "mail_id", "email_id", "e_mail", "email" -> "email";
            case "phone", "phone_number", "mobile", "mobile_number" -> "phone";
            case "mentor_classes", "mentor", "classes" -> "mentor";
            case "full_name", "student_name", "staff_name", "name" -> "name";
            default -> s;
        };
    }

    private List<Map<String, String>> readCsv(InputStream in, List<String> required) throws IOException {
        List<Map<String, String>> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String headerLine = br.readLine();
            if (headerLine == null) throw new BusinessException("The file is empty.");
            List<String> header = new ArrayList<>();
            for (String h : AdminService.parseCsvLine(headerLine)) header.add(canon(h));
            for (String need : required)
                if (!header.contains(need)) throw new BusinessException("Missing CSV column for '" + need + "'. See the Formats page for the exact header row.");
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> cells = AdminService.parseCsvLine(line);
                Map<String, String> row = new HashMap<>();
                for (int c = 0; c < header.size(); c++) row.put(header.get(c), c < cells.size() ? cells.get(c).trim() : "");
                out.add(row);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    /** Per-operation caches so a 4000-row import does not repeat the same lookups. */
    private class Ctx {
        private final Map<String, Department> byCode = new HashMap<>();
        private final Map<String, Department> byName = new HashMap<>();
        private final Map<String, ClassSection> cls = new HashMap<>();

        Ctx() {
            departments.findAll().forEach(d -> {
                byCode.put(d.getCode().toUpperCase(Locale.ROOT), d);
                byName.put(d.getName().toLowerCase(Locale.ROOT), d);
            });
        }

        Department dept(String s) {
            if (s == null || s.isBlank()) return null;
            Department d = byCode.get(s.trim().toUpperCase(Locale.ROOT));
            return d != null ? d : byName.get(s.trim().toLowerCase(Locale.ROOT));
        }

        Department unassigned() {
            Department d = byCode.get("GEN");
            if (d == null) {
                d = new Department();
                d.setCode("GEN");
                d.setName("Unassigned (set department)");
                d = departments.save(d);
                byCode.put("GEN", d);
            }
            return d;
        }

        ClassSection cls(Department d, int year, String section) {
            String key = d.getCode() + "|" + year + "|" + section.toUpperCase(Locale.ROOT);
            return cls.computeIfAbsent(key, k -> admin.ensureClass(d, year, section, semesterFor(year)));
        }
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static String req(String v, String field) {
        if (v == null || v.isBlank()) throw new BusinessException(field + " is required.");
        return v.trim();
    }

    private static String email(String v) {
        if (v == null || !EMAIL.matcher(v.trim()).matches()) throw new BusinessException("A valid e-mail address is required.");
        return v.trim().toLowerCase(Locale.ROOT);
    }

    private static String optPhone(String v) {
        if (v == null || v.isBlank()) return null;
        if (!PHONE.matcher(v.trim()).matches()) throw new BusinessException("Phone number must have 10-15 digits.");
        return v.trim();
    }
}
