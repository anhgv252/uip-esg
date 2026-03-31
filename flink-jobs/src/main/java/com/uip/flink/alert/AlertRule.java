package com.uip.flink.alert;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertRule implements Serializable {
    private String measureType;
    private String operator;    // >, >=, <, <=, ==
    private double threshold;
    private String severity;

    public boolean matches(String measureType, double value) {
        if (!this.measureType.equalsIgnoreCase(measureType)) return false;
        return switch (operator) {
            case ">"  -> value > threshold;
            case ">=" -> value >= threshold;
            case "<"  -> value < threshold;
            case "<=" -> value <= threshold;
            case "==" -> Double.compare(value, threshold) == 0;
            default   -> false;
        };
    }
}
