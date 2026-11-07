package com.psgitech.outpass.domain;

public enum RequestStatus {
    PENDING("Awaiting tutor", "bg-amber-50 text-amber-800 border-amber-200"),
    MEET_IN_PERSON("Meet tutor in person", "bg-sky-50 text-sky-800 border-sky-200"),
    PENDING_HOD("Awaiting HOD approval", "bg-indigo-50 text-indigo-800 border-indigo-200"),
    APPROVED("Approved", "bg-green-50 text-green-800 border-green-200"),
    DECLINED("Declined", "bg-red-50 text-red-800 border-red-200"),
    WITHDRAWN("Withdrawn", "bg-gray-100 text-gray-600 border-gray-200"),
    USED("Exit verified at gate", "bg-emerald-50 text-emerald-800 border-emerald-300"),
    EXPIRED("Expired", "bg-orange-50 text-orange-800 border-orange-200");

    private final String label;
    private final String badgeClass;
    RequestStatus(String label, String badgeClass) { this.label = label; this.badgeClass = badgeClass; }
    public String getLabel() { return label; }
    public String getBadgeClass() { return badgeClass; }
    public boolean isOpen() { return this == PENDING || this == MEET_IN_PERSON || this == PENDING_HOD; }
}
