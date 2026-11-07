package com.psgitech.outpass.service;

import com.psgitech.outpass.config.AppConfig;
import com.psgitech.outpass.domain.Role;
import com.psgitech.outpass.domain.UserAccount;
import com.psgitech.outpass.repo.Repos;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Admin-only data removal: delete people, or clear requests / gate scans / notifications / audit entries
 * for everyone or a chosen person, department or class (optionally within a date range).
 * The append-only protection on history tables is lifted only inside these transactions.
 */
@Service
@RequiredArgsConstructor
public class PurgeService {
    private final Repos.Users users;
    private final AuditService audit;

    @PersistenceContext
    private EntityManager em;

    public record Spec(String scope, String person, String dept, Long classId, LocalDate from, LocalDate to,
                       boolean requests, boolean scans, boolean notifications, boolean auditLog) {}

    // ------------------------------------------------------------------ history / log clean-up

    @Transactional(noRollbackFor = BusinessException.class)
    public Map<String, Integer> purge(Spec s, boolean dryRun, String actor) {
        em.flush();
        allowPurge();
        Map<String, Integer> out = new LinkedHashMap<>();
        List<Long> uids = resolveUsers(s);                       // null = everyone
        List<String> emails = null;
        if (uids != null) {
            emails = uids.isEmpty() ? List.of() : em.createQuery("select u.email from UserAccount u where u.id in :ids", String.class)
                    .setParameter("ids", uids).getResultList();
        }
        Instant from = s.from() == null ? null : s.from().atStartOfDay(AppConfig.ZONE).toInstant();
        Instant to = s.to() == null ? null : s.to().plusDays(1).atStartOfDay(AppConfig.ZONE).toInstant();

        if (s.requests()) {
            StringBuilder q = new StringBuilder("select r.id from OutpassRequest r where 1=1");
            Map<String, Object> p = new HashMap<>();
            if (uids != null) { q.append(" and r.student.user.id in :uids"); p.put("uids", uids.isEmpty() ? List.of(-1L) : uids); }
            if (s.from() != null) { q.append(" and r.permissionDate >= :df"); p.put("df", s.from()); }
            if (s.to() != null) { q.append(" and r.permissionDate <= :dt"); p.put("dt", s.to()); }
            List<Long> ids = longs(q.toString(), p);
            out.put("outpass requests (with their approval history and gate scans)", ids.size());
            if (!dryRun) deleteRequests(ids);
        }
        if (s.scans()) {
            StringBuilder q = new StringBuilder("select g.id from GateScan g where 1=1");
            Map<String, Object> p = new HashMap<>();
            if (uids != null) { q.append(" and g.request.student.user.id in :uids"); p.put("uids", uids.isEmpty() ? List.of(-1L) : uids); }
            if (from != null) { q.append(" and g.scannedAt >= :f"); p.put("f", from); }
            if (to != null) { q.append(" and g.scannedAt < :t"); p.put("t", to); }
            List<Long> ids = longs(q.toString(), p);
            out.put("gate scan records", ids.size());
            if (!dryRun) deleteIn("gate_scan", "id", ids);
        }
        if (s.notifications()) {
            StringBuilder q = new StringBuilder("select n.id from Notification n where 1=1");
            Map<String, Object> p = new HashMap<>();
            if (uids != null) { q.append(" and n.user.id in :uids"); p.put("uids", uids.isEmpty() ? List.of(-1L) : uids); }
            if (from != null) { q.append(" and n.createdAt >= :f"); p.put("f", from); }
            if (to != null) { q.append(" and n.createdAt < :t"); p.put("t", to); }
            List<Long> ids = longs(q.toString(), p);
            out.put("notifications", ids.size());
            if (!dryRun) deleteIn("notification", "id", ids);
        }
        if (s.auditLog()) {
            StringBuilder q = new StringBuilder("select a.id from AuditLog a where 1=1");
            Map<String, Object> p = new HashMap<>();
            if (emails != null) { q.append(" and a.actor in :em"); p.put("em", emails.isEmpty() ? List.of("-") : emails); }
            if (from != null) { q.append(" and a.createdAt >= :f"); p.put("f", from); }
            if (to != null) { q.append(" and a.createdAt < :t"); p.put("t", to); }
            List<Long> ids = longs(q.toString(), p);
            out.put("audit log entries", ids.size());
            if (!dryRun) deleteIn("audit_log", "id", ids);
        }
        if (!dryRun) {
            em.clear();
            audit.log(actor, "DATA_PURGE", "Purge", null, "scope=" + s.scope() + " " + out);
        }
        return out;
    }

    private List<Long> resolveUsers(Spec s) {
        switch (s.scope() == null ? "ALL" : s.scope()) {
            case "PERSON" -> {
                String key = s.person() == null ? "" : s.person().trim();
                if (key.isEmpty()) throw new BusinessException("Enter the person's e-mail or register number.");
                List<Long> ids = em.createQuery("select u.id from UserAccount u where lower(u.email) = :k "
                        + "or u.id in (select sp.user.id from StudentProfile sp where lower(sp.rollNumber) = :k)", Long.class)
                        .setParameter("k", key.toLowerCase(Locale.ROOT)).getResultList();
                if (ids.isEmpty()) throw new BusinessException("No person found for '" + key + "'.");
                return ids;
            }
            case "DEPT" -> {
                if (s.dept() == null || s.dept().isBlank()) throw new BusinessException("Select a department.");
                List<Long> ids = new ArrayList<>(em.createQuery("select sp.user.id from StudentProfile sp where sp.classSection.department.code = :d", Long.class)
                        .setParameter("d", s.dept()).getResultList());
                ids.addAll(em.createQuery("select f.user.id from FacultyProfile f where f.department.code = :d", Long.class)
                        .setParameter("d", s.dept()).getResultList());
                return ids;
            }
            case "CLASS" -> {
                if (s.classId() == null) throw new BusinessException("Select a class.");
                return em.createQuery("select sp.user.id from StudentProfile sp where sp.classSection.id = :c", Long.class)
                        .setParameter("c", s.classId()).getResultList();
            }
            default -> { return null; }
        }
    }

    // ------------------------------------------------------------------ people

    /** Permanently deletes the given accounts and everything attached to them. Admins and the acting admin are skipped. */
    @Transactional(noRollbackFor = BusinessException.class)
    public Map<String, Object> deletePeople(Collection<Long> userIds, String actorEmail) {
        em.flush();
        allowPurge();
        int deleted = 0;
        List<String> skipped = new ArrayList<>();
        for (Long id : userIds) {
            UserAccount u = users.findById(id).orElse(null);
            if (u == null) continue;
            if (u.getRole() == Role.ADMIN) { skipped.add(u.getEmail() + " (admin accounts are not deleted here)"); continue; }
            if (u.getEmail().equalsIgnoreCase(actorEmail)) { skipped.add(u.getEmail() + " (yourself)"); continue; }
            deleteOne(id, u.getRole());
            deleted++;
        }
        em.clear();
        audit.log(actorEmail, "PEOPLE_DELETED", "UserAccount", null, deleted + " deleted, " + skipped.size() + " skipped");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("deleted", deleted);
        r.put("skipped", skipped);
        return r;
    }

    /** Deletes every account of the given roles (never admins). */
    @Transactional(noRollbackFor = BusinessException.class)
    public Map<String, Object> deleteAllOfRole(Role role, String actorEmail) {
        if (role == Role.ADMIN) throw new BusinessException("Admin accounts cannot be bulk deleted.");
        List<Long> ids = em.createQuery("select u.id from UserAccount u where u.role = :r", Long.class).setParameter("r", role).getResultList();
        Map<String, Object> r = deletePeopleInternal(ids, actorEmail);
        return r;
    }

    private Map<String, Object> deletePeopleInternal(Collection<Long> ids, String actorEmail) {
        return deletePeople(ids, actorEmail);
    }

    private void deleteOne(Long id, Role role) {
        if (role == Role.STUDENT) {
            deleteRequests(longs("select r.id from OutpassRequest r where r.student.user.id = :u", Map.of("u", id)));
            exec("delete from student_profile where user_id = :u", id);
        } else {
            exec("update outpass_request set decision_owner_id = null where decision_owner_id = :u", id);
            exec("update outpass_request set hod_approver_id = null where hod_approver_id = :u", id);
            exec("delete from approval_revision where actor_id = :u", id);
            exec("update gate_scan set scanned_by_id = null where scanned_by_id = :u", id);
            exec("delete from tutor_assignment where faculty_id in (select id from faculty_profile where user_id = :u)", id);
            exec("delete from backup_tutor_assignment where faculty_id in (select id from faculty_profile where user_id = :u)", id);
            exec("delete from hod_assignment where faculty_id in (select id from faculty_profile where user_id = :u)", id);
            exec("delete from faculty_profile where user_id = :u", id);
        }
        exec("delete from notification where user_id = :u", id);
        exec("delete from notification_pref where user_id = :u", id);
        exec("delete from push_subscription where user_id = :u", id);
        exec("update gate_scan set scanned_by_id = null where scanned_by_id = :u", id);
        exec("delete from user_account where id = :u", id);
    }

    // ------------------------------------------------------------------ low level

    private void deleteRequests(List<Long> ids) {
        deleteIn("gate_scan", "request_id", ids);
        deleteIn("approval_revision", "request_id", ids);
        deleteIn("outpass_request", "id", ids);
    }

    private void deleteIn(String table, String column, List<Long> ids) {
        for (int i = 0; i < ids.size(); i += 1000) {
            List<Long> chunk = ids.subList(i, Math.min(ids.size(), i + 1000));
            em.createNativeQuery("delete from " + table + " where " + column + " in (:ids)").setParameter("ids", chunk).executeUpdate();
        }
    }

    private void exec(String sql, Long id) {
        em.createNativeQuery(sql).setParameter("u", id).executeUpdate();
    }

    private List<Long> longs(String jpql, Map<String, Object> params) {
        var q = em.createQuery(jpql, Long.class);
        params.forEach(q::setParameter);
        return q.getResultList();
    }

    private void allowPurge() {
        Query q = em.createNativeQuery("select set_config('outpass.allow_purge', 'on', true)");
        q.getSingleResult();
    }
}
