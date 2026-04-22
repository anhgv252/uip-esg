package com.uip.backend.workflow.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class FilterEvaluator {

    private final ObjectMapper objectMapper;

    public boolean matches(String filterConditionsJson, Map<String, Object> payload) {
        if (filterConditionsJson == null || filterConditionsJson.isBlank()) return true;
        try {
            List<FilterCondition> conditions = objectMapper.readValue(
                filterConditionsJson, new TypeReference<>() {});
            return conditions.stream().allMatch(cond -> evaluate(cond, payload));
        } catch (Exception e) {
            log.warn("Failed to parse filter conditions: {}", e.getMessage());
            return false;
        }
    }

    private boolean evaluate(FilterCondition cond, Map<String, Object> payload) {
        Object actual = payload.get(cond.field());
        return switch (cond.op()) {
            case "EQ"          -> Objects.equals(normalize(actual), normalize(cond.value()));
            case "NE"          -> !Objects.equals(normalize(actual), normalize(cond.value()));
            case "GT"          -> compareNumbers(actual, cond.value()) > 0;
            case "GTE"         -> compareNumbers(actual, cond.value()) >= 0;
            case "LT"          -> compareNumbers(actual, cond.value()) < 0;
            case "LTE"         -> compareNumbers(actual, cond.value()) <= 0;
            case "IN"          -> inList(cond.value(), normalize(actual));
            case "CONTAINS"    -> actual != null && actual.toString().contains(String.valueOf(cond.value()));
            case "IS_NULL"     -> actual == null;
            case "IS_NOT_NULL" -> actual != null;
            default            -> false;
        };
    }

    private Object normalize(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.doubleValue();
        return val.toString();
    }

    private int compareNumbers(Object actual, Object expected) {
        Double a = toDouble(actual);
        Double b = toDouble(expected);
        if (a == null || b == null) return 0;
        return Double.compare(a, b);
    }

    private Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(val.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private boolean inList(Object value, Object actual) {
        if (value instanceof List<?> list) return list.contains(actual);
        return false;
    }

    public record FilterCondition(String field, String op, Object value, List<Object> values) {}
}
