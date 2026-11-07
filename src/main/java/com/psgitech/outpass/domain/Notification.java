package com.psgitech.outpass.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notification")
@Getter @Setter @NoArgsConstructor
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String message;

    @Column(length = 200)
    private String link;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type = NotificationType.INFO;

    @Column(nullable = false)
    private boolean seen;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
