package com.psgitech.outpass.web;

import com.psgitech.outpass.domain.OutpassRequest;
import com.psgitech.outpass.domain.RequestStatus;
import com.psgitech.outpass.domain.UserAccount;
import com.psgitech.outpass.service.Fmt;

/** Large "what was decided" banner and the highlighted action button shown on the decision page. */
public record DecisionBanner(String headline, String detail, String css, String chosen) {

    private static String who(UserAccount owner, UserAccount viewer) {
        if (owner == null) return "A tutor";
        return viewer != null && viewer.getId().equals(owner.getId()) ? "You" : owner.getFullName();
    }

    public static DecisionBanner of(OutpassRequest r, UserAccount viewer, int maxRevisions) {
        RequestStatus s = r.getStatus();
        if (s == RequestStatus.PENDING || (s == RequestStatus.PENDING_HOD && r.getDecisionOwner() == null)) return null;
        boolean byHod = r.getHodApprover() != null;
        String owner = who(r.getDecisionOwner(), viewer);
        String hod = r.getHodApprover() == null ? "" : who(r.getHodApprover(), viewer);
        String headline, css, chosen = null;
        switch (s) {
            case APPROVED -> {
                headline = byHod ? "APPROVED by HOD " + hod : owner + " APPROVED this request";
                css = "border-green-600 bg-green-50 text-green-900";
                chosen = byHod ? "FORWARD_HOD" : "APPROVE";
            }
            case DECLINED -> {
                headline = byHod ? "DECLINED by HOD " + hod : owner + " DECLINED this request";
                css = "border-red-600 bg-red-50 text-red-900";
                chosen = byHod ? "FORWARD_HOD" : "DECLINE";
            }
            case MEET_IN_PERSON -> {
                headline = owner + " asked the student to MEET IN PERSON";
                css = "border-sky-600 bg-sky-50 text-sky-900";
                chosen = "MEET";
            }
            case PENDING_HOD -> {
                headline = owner + " ACCEPTED and forwarded this to the HOD - waiting for the HOD";
                css = "border-indigo-600 bg-indigo-50 text-indigo-900";
                chosen = "FORWARD_HOD";
            }
            case USED -> {
                headline = "Student EXITED - scanned at the gate (decision locked)";
                css = "border-emerald-600 bg-emerald-50 text-emerald-900";
                chosen = byHod ? "FORWARD_HOD" : "APPROVE";
            }
            case EXPIRED -> {
                headline = "EXPIRED - the validity window has passed";
                css = "border-orange-500 bg-orange-50 text-orange-900";
            }
            default -> {
                headline = "WITHDRAWN by the student";
                css = "border-gray-400 bg-gray-50 text-gray-800";
            }
        }
        StringBuilder d = new StringBuilder();
        Fmt fmt = new Fmt();
        String remark = byHod ? r.getHodRemark() : r.getDecisionRemark();
        var when = byHod ? r.getHodDecidedAt() : r.getDecidedAt();
        if (when != null) d.append("Recorded ").append(fmt.dt(when));
        if (remark != null && !remark.isBlank()) d.append(d.length() > 0 ? " - " : "").append("Remark: \"").append(remark).append("\"");
        if (r.getRevisionCount() > 0) d.append(d.length() > 0 ? " - " : "").append("Revisions used: ").append(r.getRevisionCount()).append(" of ").append(maxRevisions);
        return new DecisionBanner(headline, d.toString(), "border-4 " + css, chosen);
    }
}
