package com.vslauncher;

import java.math.BigDecimal;

/** Small, deterministic arithmetic parser for explicit local calculator queries. */
final class CalculatorAction {
    private static final int MAX_EXPRESSION_LENGTH = 64;
    private static final double MAX_ABS_RESULT = 1.0e15;

    private CalculatorAction() {}

    static String evaluate(String raw) {
        if (raw == null) return null;
        String expression = raw.trim();
        if (expression.startsWith("=")) expression = expression.substring(1).trim();
        if (expression.length() < 3 || expression.length() > MAX_EXPRESSION_LENGTH) return null;

        Parser parser = new Parser(expression);
        Double value = parser.parse();
        if (value == null || !parser.hasBinaryOperator) return null;
        if (!Double.isFinite(value) || Math.abs(value) > MAX_ABS_RESULT) return null;

        BigDecimal decimal = BigDecimal.valueOf(value).stripTrailingZeros();
        String formatted = decimal.toPlainString();
        return "-0".equals(formatted) ? "0" : formatted;
    }

    private static final class Parser {
        private final String input;
        private int index;
        private boolean hasBinaryOperator;

        Parser(String input) {
            this.input = input;
        }

        Double parse() {
            double value;
            try {
                value = expression();
                skipSpaces();
                if (index != input.length()) return null;
            } catch (IllegalArgumentException error) {
                return null;
            }
            return value;
        }

        private double expression() {
            double value = term();
            while (true) {
                skipSpaces();
                if (take('+')) {
                    hasBinaryOperator = true;
                    value += term();
                } else if (take('-')) {
                    hasBinaryOperator = true;
                    value -= term();
                } else {
                    return value;
                }
            }
        }

        private double term() {
            double value = factor();
            while (true) {
                skipSpaces();
                if (take('*')) {
                    hasBinaryOperator = true;
                    value *= factor();
                } else if (take('/')) {
                    hasBinaryOperator = true;
                    double divisor = factor();
                    if (divisor == 0d) throw new IllegalArgumentException();
                    value /= divisor;
                } else if (take('%')) {
                    hasBinaryOperator = true;
                    double divisor = factor();
                    if (divisor == 0d) throw new IllegalArgumentException();
                    value %= divisor;
                } else {
                    return value;
                }
            }
        }

        private double factor() {
            skipSpaces();
            if (take('+')) return factor();
            if (take('-')) return -factor();
            if (take('(')) {
                double value = expression();
                skipSpaces();
                if (!take(')')) throw new IllegalArgumentException();
                return value;
            }
            return number();
        }

        private double number() {
            skipSpaces();
            int start = index;
            boolean dot = false;
            while (index < input.length()) {
                char c = input.charAt(index);
                if (Character.isDigit(c)) {
                    index++;
                } else if (c == '.' && !dot) {
                    dot = true;
                    index++;
                } else {
                    break;
                }
            }
            if (start == index) throw new IllegalArgumentException();
            String token = input.substring(start, index);
            if (".".equals(token)) throw new IllegalArgumentException();
            try {
                return Double.parseDouble(token);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException();
            }
        }

        private boolean take(char expected) {
            if (index < input.length() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void skipSpaces() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) index++;
        }
    }
}
