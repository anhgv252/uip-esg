package com.uip.backend.admin.api;

import com.uip.backend.admin.domain.ErrorRecord;
import com.uip.backend.admin.repository.ErrorRecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/errors")
@RequiredArgsConstructor
@Tag(name = "Admin — Errors", description = "Data quality error review workflow")
public class ErrorRecordController {

    private final ErrorRecordRepository errorRecordRepository;

    @GetMapping
    @Operation(summary = "List error records with optional module/status filters")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ErrorRecord>> listErrors(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequest.of(page, Math.min(size, 100),
                Sort.by(Sort.Direction.DESC, "occurredAt"));

        Specification<ErrorRecord> spec = Specification.where(null);
        if (module != null && !module.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("sourceModule"), module));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("occurredAt"), to));
        }

        return ResponseEntity.ok(errorRecordRepository.findAll(spec, pageable));
    }

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Mark error record as resolved")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<ErrorRecord> resolve(@PathVariable UUID id) {
        ErrorRecord record = errorRecordRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Error record not found: " + id));
        record.setStatus("RESOLVED");
        record.setResolvedAt(Instant.now());
        return ResponseEntity.ok(errorRecordRepository.save(record));
    }

    @PostMapping("/{id}/reingest")
    @Operation(summary = "Mark error record for reingestion")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<ErrorRecord> reingest(@PathVariable UUID id) {
        ErrorRecord record = errorRecordRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Error record not found: " + id));
        record.setStatus("REINGESTED");
        record.setResolvedAt(Instant.now());
        return ResponseEntity.ok(errorRecordRepository.save(record));
    }
}
