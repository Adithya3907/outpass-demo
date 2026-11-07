package com.psgitech.outpass.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "class_section")
@Getter @Setter @NoArgsConstructor
public class ClassSection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "academic_year", nullable = false)
    private int academicYear;

    @Column(nullable = false, length = 10)
    private String section;

    @Column(nullable = false)
    private int semester;

    @Column(nullable = false)
    private boolean active = true;

    public String getLabel() {
        return department.getCode() + " - Year " + academicYear + " - Section " + section;
    }
}
