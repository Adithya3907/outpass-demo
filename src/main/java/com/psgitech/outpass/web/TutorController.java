package com.psgitech.outpass.web;

import com.psgitech.outpass.config.OutpassProperties;
import com.psgitech.outpass.domain.*;
import com.psgitech.outpass.repo.Repos;
import com.psgitech.outpass.service.BusinessException;
import com.psgitech.outpass.service.OutpassService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/tutor")
@RequiredArgsConstructor
public class TutorController {
    private final Repos.Users users;
    private final OutpassService service;
    private final OutpassProperties props;

    private UserAccount me(Principal p) {
        return users.findByEmailIgnoreCase(p.getName()).orElseThrow();
    }

    @GetMapping
    public String inbox(Principal p, Model m) {
        List<OutpassRequest> all = service.tutorInbox(me(p));
        m.addAttribute("needsAction", all.stream()
                .filter(r -> r.getStatus() == RequestStatus.PENDING || r.getStatus() == RequestStatus.MEET_IN_PERSON).toList());
        m.addAttribute("others", all.stream()
                .filter(r -> r.getStatus() != RequestStatus.PENDING && r.getStatus() != RequestStatus.MEET_IN_PERSON).toList());
        return "tutor/inbox";
    }

    @GetMapping("/requests/{id}")
    public String detail(@PathVariable Long id, Principal p, Model m) {
        UserAccount me = me(p);
        OutpassRequest r = service.getForTutor(id, me);
        boolean locked = r.getStatus() == RequestStatus.WITHDRAWN || r.getStatus() == RequestStatus.USED || r.getStatus() == RequestStatus.EXPIRED;
        boolean otherOwner = r.getDecisionOwner() != null && !r.getDecisionOwner().getId().equals(me.getId());
        boolean revisionState = r.getStatus() == RequestStatus.APPROVED || r.getStatus() == RequestStatus.DECLINED
                || r.getStatus() == RequestStatus.PENDING_HOD;
        int left = Math.max(0, props.getMaxRevisions() - r.getRevisionCount());
        boolean canAct = !locked && !otherOwner && !(revisionState && left == 0);
        m.addAttribute("r", r);
        m.addAttribute("timeline", service.timeline(r));
        m.addAttribute("actions", TutorAction.values());
        m.addAttribute("postUrl", "/tutor/requests/" + id + "/decision");
        m.addAttribute("backUrl", "/tutor");
        m.addAttribute("canAct", canAct);
        m.addAttribute("revisionsLeft", left);
        m.addAttribute("banner", DecisionBanner.of(r, me, props.getMaxRevisions()));
        m.addAttribute("lockedMessage", locked ? (r.getStatus() == RequestStatus.USED
                ? "The outpass has been scanned at the gate - decisions are locked."
                : r.getStatus() == RequestStatus.EXPIRED ? "This request expired (its validity window has passed). The student must submit a new request."
                : "The student withdrew this request.")
                : otherOwner ? "Handled by " + r.getDecisionOwner().getFullName() + " (decision owner)."
                : (revisionState && left == 0) ? "All permitted revisions have been used." : null);
        return "staff/request";
    }

    @PostMapping("/requests/{id}/decision")
    public String decide(@PathVariable Long id, @RequestParam TutorAction action,
                         @RequestParam(required = false) String remark, Principal p, RedirectAttributes ra) {
        try {
            service.tutorDecide(id, me(p), action, remark);
            ra.addFlashAttribute("msg", "Decision recorded: " + action.getLabel() + ".");
        } catch (BusinessException e) {
            ra.addFlashAttribute("err", e.getMessage());
        }
        return "redirect:/tutor/requests/" + id;
    }
}
