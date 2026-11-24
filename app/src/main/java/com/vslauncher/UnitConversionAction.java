package com.vslauncher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Pure-Java, I/O-free local unit conversion parser for latent Search queries.
 *
 * Supports concise expressions such as "10km to mi", "72f to c", "500g in oz",
 * "180cm to ft", or "2.5 l to gal".
 */
final class UnitConversionAction {
    static final class Conversion {
        final String display;
        final String copyValue;

        Conversion(String display, String copyValue) {
            this.display = display;
            this.copyValue = copyValue;
        }
    }

    private static final int DIM_LENGTH = 1;
    private static final int DIM_MASS = 2;
    private static final int DIM_VOLUME = 3;
    private static final int DIM_SPEED = 4;
    private static final int DIM_TEMP = 5;

    private UnitConversionAction() {}

    static Conversion evaluate(String rawQuery) {
        if (rawQuery == null) return null;
        String trimmed = rawQuery.trim();
        if (trimmed.length() < 4 || trimmed.length() > 48) return null;

        int i = 0;
        int len = trimmed.length();
        boolean negative = false;
        if (trimmed.charAt(i) == '-') {
            negative = true;
            i++;
        } else if (trimmed.charAt(i) == '+') {
            i++;
        }

        int numStart = i;
        boolean sawDigit = false;
        boolean sawDot = false;
        while (i < len) {
            char c = trimmed.charAt(i);
            if (c >= '0' && c <= '9') {
                sawDigit = true;
                i++;
            } else if (c == '.' && !sawDot) {
                sawDot = true;
                i++;
            } else {
                break;
            }
        }
        if (!sawDigit) return null;

        double value;
        try {
            value = Double.parseDouble(trimmed.substring(numStart, i));
        } catch (NumberFormatException error) {
            return null;
        }
        if (negative) value = -value;
        if (!Double.isFinite(value)) return null;

        while (i < len && Character.isWhitespace(trimmed.charAt(i))) i++;
        int fromStart = i;
        while (i < len && Character.isLetter(trimmed.charAt(i))) i++;
        if (i == fromStart) return null;
        String fromToken = trimmed.substring(fromStart, i).toLowerCase(Locale.ROOT);

        while (i < len && Character.isWhitespace(trimmed.charAt(i))) i++;
        if (i >= len) return null;

        // Require an explicit connector ("to", "in", or "->") so ordinary words don't misfire.
        if (trimmed.startsWith("->", i)) {
            i += 2;
        } else {
            int connStart = i;
            while (i < len && Character.isLetter(trimmed.charAt(i))) i++;
            String conn = trimmed.substring(connStart, i).toLowerCase(Locale.ROOT);
            if (!"to".equals(conn) && !"in".equals(conn)) return null;
        }

        while (i < len && Character.isWhitespace(trimmed.charAt(i))) i++;
        int toStart = i;
        while (i < len && Character.isLetter(trimmed.charAt(i))) i++;
        if (i == toStart) return null;
        String toToken = trimmed.substring(toStart, i).toLowerCase(Locale.ROOT);

        while (i < len && Character.isWhitespace(trimmed.charAt(i))) i++;
        if (i != len) return null;

        Unit from = resolveUnit(fromToken);
        Unit to = resolveUnit(toToken);
        if (from == null || to == null || from.dimension != to.dimension) return null;
        if (from.canonical.equals(to.canonical)) return null;

        double converted;
        if (from.dimension == DIM_TEMP) {
            converted = convertTemp(value, from.canonical, to.canonical);
        } else {
            double base = value * from.toBaseFactor;
            converted = base / to.toBaseFactor;
        }
        if (!Double.isFinite(converted)) return null;

        String formattedValue = formatNumber(converted);
        String copyValue = formattedValue + " " + to.canonical;
        String display = formatNumber(value) + " " + from.canonical + " = " + copyValue;
        return new Conversion(display, copyValue);
    }

    private static double convertTemp(double value, String from, String to) {
        double celsius;
        if ("F".equals(from)) celsius = (value - 32.0) * 5.0 / 9.0;
        else if ("K".equals(from)) celsius = value - 273.15;
        else celsius = value;

        if ("F".equals(to)) return celsius * 9.0 / 5.0 + 32.0;
        if ("K".equals(to)) return celsius + 273.15;
        return celsius;
    }

    private static String formatNumber(double value) {
        if (Math.abs(value) < 1e-9) return "0";
        BigDecimal bd = BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return bd.toPlainString();
    }

    private static Unit resolveUnit(String token) {
        switch (token) {
            // Length (base: meters)
            case "mm": return new Unit(DIM_LENGTH, "mm", 0.001);
            case "cm": return new Unit(DIM_LENGTH, "cm", 0.01);
            case "m":
            case "meter":
            case "meters": return new Unit(DIM_LENGTH, "m", 1.0);
            case "km":
            case "kilometer":
            case "kilometers": return new Unit(DIM_LENGTH, "km", 1000.0);
            case "in":
            case "inch":
            case "inches": return new Unit(DIM_LENGTH, "in", 0.0254);
            case "ft":
            case "foot":
            case "feet": return new Unit(DIM_LENGTH, "ft", 0.3048);
            case "yd":
            case "yard":
            case "yards": return new Unit(DIM_LENGTH, "yd", 0.9144);
            case "mi":
            case "mile":
            case "miles": return new Unit(DIM_LENGTH, "mi", 1609.344);

            // Mass (base: grams)
            case "mg": return new Unit(DIM_MASS, "mg", 0.001);
            case "g":
            case "gram":
            case "grams": return new Unit(DIM_MASS, "g", 1.0);
            case "kg":
            case "kilogram":
            case "kilograms": return new Unit(DIM_MASS, "kg", 1000.0);
            case "oz":
            case "ounce":
            case "ounces": return new Unit(DIM_MASS, "oz", 28.349523125);
            case "lb":
            case "lbs":
            case "pound":
            case "pounds": return new Unit(DIM_MASS, "lb", 453.59237);

            // Volume (base: milliliters)
            case "ml": return new Unit(DIM_VOLUME, "ml", 1.0);
            case "l":
            case "liter":
            case "liters": return new Unit(DIM_VOLUME, "L", 1000.0);
            case "floz": return new Unit(DIM_VOLUME, "fl oz", 29.5735295625);
            case "cup":
            case "cups": return new Unit(DIM_VOLUME, "cup", 236.5882365);
            case "gal":
            case "gallon":
            case "gallons": return new Unit(DIM_VOLUME, "gal", 3785.411784);

            // Speed (base: m/s)
            case "kph":
            case "kmh": return new Unit(DIM_SPEED, "km/h", 1.0 / 3.6);
            case "mph": return new Unit(DIM_SPEED, "mph", 0.44704);
            case "mps": return new Unit(DIM_SPEED, "m/s", 1.0);

            // Temperature
            case "c":
            case "celsius": return new Unit(DIM_TEMP, "C", 1.0);
            case "f":
            case "fahrenheit": return new Unit(DIM_TEMP, "F", 1.0);
            case "k":
            case "kelvin": return new Unit(DIM_TEMP, "K", 1.0);

            default: return null;
        }
    }

    private static final class Unit {
        final int dimension;
        final String canonical;
        final double toBaseFactor;

        Unit(int dimension, String canonical, double toBaseFactor) {
            this.dimension = dimension;
            this.canonical = canonical;
            this.toBaseFactor = toBaseFactor;
        }
    }
}
