package com.psgitech.outpass.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "audit_log")
@Getter @Setter @NoArgsConstructor
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 190)
    private String actor;

    @Column(nullable = false, length = 60)
    private String action;

    @Column(length = 60)
    private String entity;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(length = 500)
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
