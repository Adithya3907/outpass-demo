package com.psgitech.outpass.repo;

import com.psgitech.outpass.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** All Spring Data repositories, grouped in one file for easy navigation. */
public final class Repos {
    private Repos() {}

    public interface Users extends JpaRepository<UserAccount, Long> {
        Optional<UserAccount> findByEmailIgnoreCase(String email);
        boolean existsByEmailIgnoreCase(String email);
        long countByRole(Role role);

        @Query("select lower(u.email) from UserAccount u")
        List<String> allEmailsLower();

        @Query("select u from UserAccount u where lower(u.fullName) like lower(concat('%', :q, '%')) "
                + "or lower(u.email) like lower(concat('%', :q, '%')) order by u.id desc")
        List<UserAccount> search(@Param("q") String q, Pageable pageable);
    }

    public interface Departments extends JpaRepository<Department, Long> {
        Optional<Department> findByCodeIgnoreCase(String code);
        List<Department> findAllByOrderByCodeAsc();
    }

    public interface ClassSections extends JpaRepository<ClassSection, Long> {
        @Query("select c from ClassSection c where c.department = :d and c.academicYear = :y and c.section = :s")
        Optional<ClassSection> find(@Param("d") Department d, @Param("y") int year, @Param("s") String section);

        @Query("select c from ClassSection c order by c.department.code, c.academicYear, c.section")
        List<ClassSection> findAllOrdered();
    }

    public interface Students extends JpaRepository<StudentProfile, Long> {
        Optional<StudentProfile> findByUser(UserAccount user);
        Optional<StudentProfile> findByRollNumberIgnoreCase(String roll);
        boolean existsByRollNumberIgnoreCase(String roll);

        @Query("select lower(s.rollNumber) from StudentProfile s")
        List<String> allRollsLower();

        @Query("select s from StudentProfile s where s.classSection.department.id = :did and "
                + "(lower(s.rollNumber) like lower(concat('%', :q, '%')) or lower(s.user.fullName) like lower(concat('%', :q, '%'))) "
                + "order by s.rollNumber")
        List<StudentProfile> searchInDepartment(@Param("did") Long deptId, @Param("q") String q, Pageable pageable);

        @Query("select s from StudentProfile s where "
                + "lower(s.rollNumber) like lower(concat('%', :q, '%')) or lower(s.user.fullName) like lower(concat('%', :q, '%')) "
                + "order by s.rollNumber")
        List<StudentProfile> search(@Param("q") String q, Pageable pageable);
    }

    public interface Faculty extends JpaRepository<FacultyProfile, Long> {
        Optional<FacultyProfile> findByUser(UserAccount user);
        @Query("select f from FacultyProfile f order by f.department.code, f.user.fullName")
        List<FacultyProfile> findAllOrdered();

        @Query("select f from FacultyProfile f where f.department.id = :did and f.user.role = :role and f.user.enabled = true order by f.user.fullName")
        List<FacultyProfile> findByDepartmentAndRole(@Param("did") Long deptId, @Param("role") Role role);
    }

    public interface TutorAssignments extends JpaRepository<TutorAssignment, Long> {
        @Query("select t from TutorAssignment t where t.classSection.id = :cid and t.effectiveFrom <= :d "
                + "and (t.effectiveTo is null or t.effectiveTo >= :d) order by t.id")
        List<TutorAssignment> activeForClass(@Param("cid") Long classId, @Param("d") LocalDate date);

        @Query("select t.classSection.id from TutorAssignment t where t.faculty.id = :fid and t.effectiveFrom <= :d "
                + "and (t.effectiveTo is null or t.effectiveTo >= :d)")
        List<Long> activeClassIdsForFaculty(@Param("fid") Long facultyId, @Param("d") LocalDate date);

        @Query("select t from TutorAssignment t order by t.classSection.department.code, t.classSection.academicYear, "
                + "t.classSection.section, t.id desc")
        List<TutorAssignment> findAllOrdered();
    }

    public interface BackupAssignments extends JpaRepository<BackupTutorAssignment, Long> {
        List<BackupTutorAssignment> findByDepartmentAndActiveTrue(Department department);
        boolean existsByFacultyAndDepartmentAndActiveTrue(FacultyProfile faculty, Department department);
        boolean existsByFacultyAndActiveTrue(FacultyProfile faculty);
        List<BackupTutorAssignment> findByActiveTrue();
    }

    public interface HodAssignments extends JpaRepository<HodAssignment, Long> {
        Optional<HodAssignment> findByDepartmentAndActiveTrue(Department department);
        Optional<HodAssignment> findByFacultyAndActiveTrue(FacultyProfile faculty);
        List<HodAssignment> findByActiveTrue();
    }

    public interface Gates extends JpaRepository<Gate, Long> {
        List<Gate> findByActiveTrueOrderByName();
        List<Gate> findAllByOrderByName();
    }

    public interface Requests extends JpaRepository<OutpassRequest, Long> {
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select r from OutpassRequest r where r.id = :id")
        Optional<OutpassRequest> lockById(@Param("id") Long id);

        Optional<OutpassRequest> findByQrToken(String token);

        List<OutpassRequest> findByStudentOrderByCreatedAtDesc(StudentProfile student);

        @Query("select r from OutpassRequest r where r.escalation = :esc and r.classSection.id in :cids order by r.createdAt desc")
        List<OutpassRequest> forClasses(@Param("esc") Escalation esc, @Param("cids") Collection<Long> classIds, Pageable pageable);

        @Query("select r from OutpassRequest r where r.escalation = :esc and r.classSection.department.id = :did order by r.createdAt desc")
        List<OutpassRequest> forDepartment(@Param("esc") Escalation esc, @Param("did") Long deptId, Pageable pageable);

        @Query("select r from OutpassRequest r where r.classSection.department.id = :did order by r.createdAt desc")
        List<OutpassRequest> allInDepartment(@Param("did") Long deptId, Pageable pageable);

        /** Requests of this student whose validity window overlaps [from, to). */
        @Query("select r from OutpassRequest r where r.student = :s and r.status in :statuses "
                + "and r.validFrom < :to and r.expiresAt > :from order by r.validFrom")
        List<OutpassRequest> overlapping(@Param("s") StudentProfile s, @Param("statuses") Collection<RequestStatus> statuses,
                                         @Param("from") Instant from, @Param("to") Instant to);

        @Query("select r from OutpassRequest r where r.status in :statuses and r.expiresAt < :now")
        List<OutpassRequest> findDue(@Param("statuses") Collection<RequestStatus> statuses, @Param("now") Instant now);

        /** Race-safe expiry: only flips rows that are still live and really past their window. */
        @Modifying(flushAutomatically = true)
        @Query("update OutpassRequest r set r.status = :expired where r.id = :id and r.status in :statuses and r.expiresAt < :now")
        int markExpired(@Param("id") Long id, @Param("statuses") Collection<RequestStatus> statuses,
                        @Param("now") Instant now, @Param("expired") RequestStatus expired);

        /** Atomic single-use consumption: exactly one concurrent caller can win this UPDATE. */
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("update OutpassRequest r set r.status = :used, r.usedAt = :now, r.usedGate = :gate "
                + "where r.id = :id and r.status = :approved and r.qrToken is not null")
        int markUsed(@Param("id") Long id, @Param("now") Instant now, @Param("gate") Gate gate,
                     @Param("used") RequestStatus used, @Param("approved") RequestStatus approved);

        @Query("select r from OutpassRequest r order by r.createdAt desc")
        List<OutpassRequest> allRecent(Pageable pageable);

        @Query("select r.classSection.department.code, r.status, count(r) from OutpassRequest r group by r.classSection.department.code, r.status")
        List<Object[]> countByDepartmentAndStatus();

        @Query("select r.status, count(r) from OutpassRequest r group by r.status")
        List<Object[]> countAllByStatus();

        @Query("select r.status, count(r) from OutpassRequest r where r.classSection.department.id = :did group by r.status")
        List<Object[]> countByStatus(@Param("did") Long deptId);

        @Query("select r.classSection.id, count(r) from OutpassRequest r where r.classSection.department.id = :did group by r.classSection.id")
        List<Object[]> countByClass(@Param("did") Long deptId);

        long countByStatus(RequestStatus status);

        List<OutpassRequest> findByStatusOrderByValidFromAsc(RequestStatus status);
    }

    public interface Revisions extends JpaRepository<ApprovalRevision, Long> {
        List<ApprovalRevision> findByRequestOrderByCreatedAtAsc(OutpassRequest request);
    }

    public interface Scans extends JpaRepository<GateScan, Long> {
        @Query("select g from GateScan g where g.scannedAt >= :from and g.scannedAt < :to order by g.scannedAt desc")
        List<GateScan> between(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

        @Query("select g from GateScan g where g.request.student.id = :sid order by g.scannedAt desc")
        List<GateScan> forStudent(@Param("sid") Long studentId, Pageable pageable);
    }

    public interface Notifications extends JpaRepository<Notification, Long> {
        List<Notification> findTop50ByUserOrderByCreatedAtDesc(UserAccount user);
        long countByUserAndSeenFalse(UserAccount user);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("update Notification n set n.seen = true where n.user = :u and n.seen = false")
        int markAllSeen(@Param("u") UserAccount user);
    }

    public interface AuditLogs extends JpaRepository<AuditLog, Long> {
        List<AuditLog> findTop300ByOrderByIdDesc();
    }

    public interface NotificationPrefs extends JpaRepository<NotificationPref, Long> {
        List<NotificationPref> findByUser(UserAccount user);
        Optional<NotificationPref> findByUserAndType(UserAccount user, NotificationType type);
    }

    public interface PushSubscriptions extends JpaRepository<PushSubscription, Long> {
        List<PushSubscription> findByUser(UserAccount user);
        Optional<PushSubscription> findByEndpoint(String endpoint);
        long countByUser(UserAccount user);
    }

    public interface AppSettings extends JpaRepository<AppSetting, String> { }
}
