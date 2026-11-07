package com.psgitech.outpass.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "approval_revision")
@Getter @Setter @NoArgsConstructor
public class ApprovalRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id")
    private OutpassRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id")
    private UserAccount actor;

    @Column(nullable = false, length = 30)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private RequestStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private RequestStatus newStatus;

    @Column(length = 500)
    private String remark;

    @Column(name = "revision_no", nullable = false)
    private int revisionNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
