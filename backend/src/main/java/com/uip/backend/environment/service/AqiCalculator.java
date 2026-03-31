package com.uip.backend.environment.service;

import org.springframework.stereotype.Component;

/**
 * AQI calculator using US EPA formula.
 * I_p = ((I_Hi - I_Lo) / (BP_Hi - BP_Lo)) * (C_p - BP_Lo) + I_Lo
 *
 * Reference: https://www.airnow.gov/sites/default/files/2020-05/aqi-technical-assistance-document-sept2018.pdf
 */
@Component
public class AqiCalculator {

    // ─── PM2.5 breakpoints (µg/m³, 24-h average) ─────────────────────────────
    private static final double[][] PM25_BP = {
        {0.0,   12.0,   0,   50},
        {12.1,  35.4,   51,  100},
        {35.5,  55.4,   101, 150},
        {55.5,  150.4,  151, 200},
        {150.5, 250.4,  201, 300},
        {250.5, 350.4,  301, 400},
        {350.5, 500.4,  401, 500}
    };

    // ─── PM10 breakpoints (µg/m³, 24-h average) ──────────────────────────────
    private static final double[][] PM10_BP = {
        {0,    54,    0,   50},
        {55,   154,   51,  100},
        {155,  254,   101, 150},
        {255,  354,   151, 200},
        {355,  424,   201, 300},
        {425,  504,   301, 400},
        {505,  604,   401, 500}
    };

    // ─── O3 breakpoints (ppb, 8-h average) ───────────────────────────────────
    private static final double[][] O3_BP = {
        {0,    54,    0,   50},
        {55,   70,    51,  100},
        {71,   85,    101, 150},
        {86,   105,   151, 200},
        {106,  200,   201, 300}
    };

    // ─── NO2 breakpoints (ppb, 1-h average) ──────────────────────────────────
    private static final double[][] NO2_BP = {
        {0,    53,    0,   50},
        {54,   100,   51,  100},
        {101,  360,   101, 150},
        {361,  649,   151, 200},
        {650,  1249,  201, 300},
        {1250, 1649,  301, 400},
        {1650, 2049,  401, 500}
    };

    // ─── SO2 breakpoints (ppb, 1-h average) ──────────────────────────────────
    private static final double[][] SO2_BP = {
        {0,    35,    0,   50},
        {36,   75,    51,  100},
        {76,   185,   101, 150},
        {186,  304,   151, 200},
        {305,  604,   201, 300},
        {605,  804,   301, 400},
        {805,  1004,  401, 500}
    };

    // ─── CO breakpoints (ppm, 8-h average) ───────────────────────────────────
    private static final double[][] CO_BP = {
        {0.0,  4.4,   0,   50},
        {4.5,  9.4,   51,  100},
        {9.5,  12.4,  101, 150},
        {12.5, 15.4,  151, 200},
        {15.5, 30.4,  201, 300},
        {30.5, 40.4,  301, 400},
        {40.5, 50.4,  401, 500}
    };

    /**
     * Calculate overall AQI as max of individual pollutant AQIs.
     *
     * @return overall AQI (0-500+), or null if all pollutants are null
     */
    public Integer calculateAqi(Double pm25, Double pm10, Double o3, Double no2, Double so2, Double co) {
        Integer max = null;
        if (pm25  != null) max = maxOrSet(max, subIndex(pm25, PM25_BP));
        if (pm10  != null) max = maxOrSet(max, subIndex(pm10, PM10_BP));
        if (o3    != null) max = maxOrSet(max, subIndex(o3,   O3_BP));
        if (no2   != null) max = maxOrSet(max, subIndex(no2,  NO2_BP));
        if (so2   != null) max = maxOrSet(max, subIndex(so2,  SO2_BP));
        if (co    != null) max = maxOrSet(max, subIndex(co,   CO_BP));
        return max;
    }

    /**
     * Return AQI category label for a given AQI value.
     */
    public String categoryLabel(int aqi) {
        if (aqi <= 50)  return "Good";
        if (aqi <= 100) return "Moderate";
        if (aqi <= 150) return "Unhealthy for Sensitive Groups";
        if (aqi <= 200) return "Unhealthy";
        if (aqi <= 300) return "Very Unhealthy";
        return "Hazardous";
    }

    /**
     * Return hex color for AQI category.
     */
    public String categoryColor(int aqi) {
        if (aqi <= 50)  return "#00E400";  // Green
        if (aqi <= 100) return "#FFFF00";  // Yellow
        if (aqi <= 150) return "#FF7E00";  // Orange
        if (aqi <= 200) return "#FF0000";  // Red
        if (aqi <= 300) return "#8F3F97";  // Purple
        return "#7E0023";                  // Maroon
    }

    /**
     * Compute sub-index for a single pollutant using linear interpolation.
     */
    private int subIndex(double concentration, double[][] breakpoints) {
        for (double[] bp : breakpoints) {
            double bpLo = bp[0], bpHi = bp[1];
            double iLo  = bp[2], iHi  = bp[3];
            if (concentration <= bpHi) {
                double index = ((iHi - iLo) / (bpHi - bpLo)) * (concentration - bpLo) + iLo;
                return (int) Math.round(index);
            }
        }
        // Concentration above top breakpoint — return max index
        return 500;
    }

    private Integer maxOrSet(Integer current, int candidate) {
        return current == null ? candidate : Math.max(current, candidate);
    }
}
