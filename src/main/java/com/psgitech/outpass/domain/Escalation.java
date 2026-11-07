package com.psgitech.outpass.domain;

public enum Escalation {
    TUTOR("My Tutors"),
    BACKUP("Backup Tutors"),
    HOD("Head of Department directly");

    private final String label;
    Escalation(String label) { this.label = label; }
    public String getLabel() { return label; }
}
