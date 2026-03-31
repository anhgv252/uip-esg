package com.uip.backend.esg.repository;

import com.uip.backend.esg.domain.EsgReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EsgReportRepository extends JpaRepository<EsgReport, UUID> {
}
