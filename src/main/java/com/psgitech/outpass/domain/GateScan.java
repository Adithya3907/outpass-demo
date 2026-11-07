package com.psgitech.outpass.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "gate_scan")
@Getter @Setter @NoArgsConstructor
public class GateScan {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private OutpassRequest request;

    @Column(name = "token_hint", length = 12)
    private String tokenHint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gate_id")
    private Gate gate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scanned_by_id")
    private UserAccount scannedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ScanResult result;

    @Column(length = 200)
    private String reason;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt = Instant.now();
}
