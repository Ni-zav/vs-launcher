package com.vslauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class CalculatorActionTest {
    @Test public void evaluatesBasicPrecedenceAndParentheses() {
        assertEquals("391", CalculatorAction.evaluate("23*17"));
        assertEquals("14", CalculatorAction.evaluate("2 + 3 * 4"));
        assertEquals("20", CalculatorAction.evaluate("(2 + 3) * 4"));
        assertEquals("2.5", CalculatorAction.evaluate("10 / 4"));
        assertEquals("1", CalculatorAction.evaluate("10 % 3"));
    }

    @Test public void supportsUnarySignsWithoutTreatingSingleNumbersAsQueries() {
        assertEquals("-6", CalculatorAction.evaluate("-2*3"));
        assertEquals("5", CalculatorAction.evaluate("= 2 + 3"));
        assertNull(CalculatorAction.evaluate("42"));
    }

    @Test public void rejectsInvalidDangerousOrUnboundedExpressions() {
        assertNull(CalculatorAction.evaluate("1/0"));
        assertNull(CalculatorAction.evaluate("hello+1"));
        assertNull(CalculatorAction.evaluate("2++"));
        assertNull(CalculatorAction.evaluate("999999999999999*999999999999999"));
    }
}
