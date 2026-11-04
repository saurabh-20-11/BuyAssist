package ai.buyassist.controller;

import ai.buyassist.audit.AuditEvent;
import ai.buyassist.audit.AuditTrail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Exposes the ordered audit timeline for a correlation id. */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditTrail auditTrail;

    public AuditController(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    @GetMapping("/{correlationId}")
    public ResponseEntity<List<AuditEvent>> timeline(@PathVariable String correlationId) {
        if (!auditTrail.exists(correlationId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(auditTrail.timeline(correlationId));
    }
}
