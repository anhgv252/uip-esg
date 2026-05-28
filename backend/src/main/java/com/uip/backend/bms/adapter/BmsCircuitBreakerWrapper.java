package com.uip.backend.bms.adapter;

import com.uip.backend.bms.api.dto.BmsCommand;
import com.uip.backend.bms.domain.BmsReading;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class BmsCircuitBreakerWrapper {

    private static final String CB_NAME = "bms-adapter";

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "pollFallback")
    public List<BmsReading> safePoll(BmsProtocolAdapter adapter) {
        return adapter.poll();
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "commandFallback")
    public void safeSendCommand(BmsProtocolAdapter adapter, BmsCommand command) {
        adapter.sendCommand(command);
    }

    @SuppressWarnings("unused")
    private List<BmsReading> pollFallback(BmsProtocolAdapter adapter, Throwable t) {
        log.warn("CB [{}] OPEN — poll fallback for {}: {}", CB_NAME, adapter.getProtocol(), t.getMessage());
        return List.of();
    }

    @SuppressWarnings("unused")
    private void commandFallback(BmsProtocolAdapter adapter, BmsCommand command, Throwable t) {
        log.error("CB [{}] OPEN — command fallback for {}: {}", CB_NAME, adapter.getProtocol(), t.getMessage());
        throw new BmsAdapterException("Circuit breaker open for " + adapter.getProtocol(), t);
    }
}
