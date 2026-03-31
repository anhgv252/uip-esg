package com.uip.backend.admin.repository;

import com.uip.backend.admin.domain.ErrorRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ErrorRecordRepository extends JpaRepository<ErrorRecord, UUID>, JpaSpecificationExecutor<ErrorRecord> {
}
