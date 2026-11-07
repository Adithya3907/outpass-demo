package com.psgitech.outpass.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "outpass_request")
@Getter @Setter @NoArgsConstructor
public class OutpassRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id")
    private StudentProfile student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_section_id")
    private ClassSection classSection;

    @Column(nullable = false, length = 255)
    private String reason;

    @Column(name = "permission_date", nullable = false)
    private LocalDate permissionDate;

    @Column(name = "exit_time", nullable = false)
    private LocalTime exitTime;

    @Column(name = "expected_return_time")
    private LocalTime expectedReturnTime;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Escalation escalation = Escalation.TUTOR;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.PENDING;

    /** Snapshot of the class tutors at submission time, so history survives later re-mapping. */
    @Column(name = "assigned_tutor_names", length = 300)
    private String assignedTutorNames;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decision_owner_id")
    private UserAccount decisionOwner;

    @Column(name = "decision_remark", length = 500)
    private String decisionRemark;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "revision_count", nullable = false)
    private int revisionCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hod_approver_id")
    private UserAccount hodApprover;

    @Column(name = "hod_remark", length = 500)
    private String hodRemark;

    @Column(name = "hod_decided_at")
    private Instant hodDecidedAt;

    @Column(name = "qr_token", unique = true, length = 64)
    private String qrToken;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_gate_id")
    private Gate usedGate;

    /** The pass can be used at the gate only inside [validFrom, expiresAt]. */
    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Version
    private long version;

    public String getOutpassCode() {
        return "OP-" + permissionDate.getYear() + "-" + String.format("%06d", id);
    }

    /** The faculty member who actually granted the final approval. */
    public UserAccount getApprovingFaculty() {
        return hodApprover != null ? hodApprover : decisionOwner;
    }

    /** Student may withdraw only while no tutor/HOD has acted. */
    public boolean isWithdrawable() {
        return decisionOwner == null && hodApprover == null
                && (status == RequestStatus.PENDING
                || (status == RequestStatus.PENDING_HOD && escalation == Escalation.HOD));
    }
}
