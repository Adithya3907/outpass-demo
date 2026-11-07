package com.psgitech.outpass.domain;

public enum TutorAction {
    APPROVE("Approve", "bg-green-700 hover:bg-green-800 text-white"),
    DECLINE("Decline", "bg-red-700 hover:bg-red-800 text-white"),
    MEET("Meet me in person", "bg-sky-700 hover:bg-sky-800 text-white"),
    FORWARD_HOD("Accept & request HOD", "bg-indigo-700 hover:bg-indigo-800 text-white");

    private final String label;
    private final String btnClass;
    TutorAction(String label, String btnClass) { this.label = label; this.btnClass = btnClass; }
    public String getLabel() { return label; }
    public String getBtnClass() { return btnClass; }
}
