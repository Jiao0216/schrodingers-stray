package com.catrescue.api.util;

/**
 * Coordinates rounded for public heatmaps (~ km-scale when decimals = 2).
 */
public final class CoordinatePrecision {

    private CoordinatePrecision() {
    }

    public static double roundDegrees(double value, int decimals) {
        if (decimals <= 0) {
            return Math.round(value);
        }
        double pow = Math.pow(10, decimals);
        return Math.round(value * pow) / pow;
    }
}
