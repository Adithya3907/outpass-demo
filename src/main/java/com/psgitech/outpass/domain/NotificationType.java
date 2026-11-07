package com.psgitech.outpass.domain;

import java.util.EnumSet;
import java.util.Set;

/** Categories a user can switch on/off (push to phone and e-mail) in Settings. */
public enum NotificationType {
    NEW_REQUEST("New requests waiting for my action", "A student sends a request that needs your decision."),
    DECISION("Decisions on my requests", "Approved, declined, meet-in-person or forwarded to the HOD."),
    EXPIRY("Pass expiry", "Your approved outpass has expired and can no longer be scanned."),
    GATE_EXIT("Gate confirmation", "Your outpass was scanned and verified at the college gate."),
    HOD_UPDATE("HOD decisions on forwarded requests", "The HOD approved or declined a request you forwarded."),
    INFO("General", "Other messages");

    private final String label;
    private final String description;
    NotificationType(String label, String description) { this.label = label; this.description = description; }
    public String getLabel() { return label; }
    public String getDescription() { return description; }

    /** Which categories are relevant to which role (INFO is never shown as a switch). */
    public static Set<NotificationType> forRole(Role role) {
        return switch (role) {
            case STUDENT -> EnumSet.of(DECISION, EXPIRY, GATE_EXIT);
            case TUTOR -> EnumSet.of(NEW_REQUEST, HOD_UPDATE);
            case HOD -> EnumSet.of(NEW_REQUEST);
            default -> EnumSet.noneOf(NotificationType.class);
        };
    }
}
