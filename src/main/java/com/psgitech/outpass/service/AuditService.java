package com.psgitech.outpass.service;

import com.psgitech.outpass.domain.AuditLog;
import com.psgitech.outpass.repo.Repos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final Repos.AuditLogs logs;

    public void log(String actor, String action, String entity, Long entityId, String details) {
        AuditLog a = new AuditLog();
        a.setActor(actor);
        a.setAction(action);
        a.setEntity(entity);
        a.setEntityId(entityId);
        a.setDetails(details != null && details.length() > 490 ? details.substring(0, 490) : details);
        logs.save(a);
    }
}
