package com.uip.analytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * analytics-service — standalone service, owner của ClickHouse.
 *
 * Trách nhiệm:
 *   - Nhận OLAP queries từ monolith (hoặc trực tiếp từ frontend qua Kong)
 *   - Query ClickHouse cho cross-building analytics, ESG aggregates
 *   - Không biết gì về TimescaleDB, Kafka, hay monolith internals
 *
 * Không import bất kỳ class nào từ backend/ monolith.
 * Shared types (EsgAggregateResult, v.v.) được định nghĩa lại ở đây.
 * In production: extract thành shared-api-types library (Maven artifact).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class UipAnalyticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UipAnalyticsServiceApplication.class, args);
    }
}
