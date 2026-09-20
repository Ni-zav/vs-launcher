package com.vslauncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SearchCommandTest {
    @Test public void commandMatchingIsPrefixDriven() {
        assertTrue(SearchCommand.matching("wi").stream()
                .anyMatch(command -> SearchCommand.WIFI.equals(command.id)));
        assertTrue(SearchCommand.matching("home set").stream()
                .anyMatch(command -> SearchCommand.LAUNCHER_SETTINGS.equals(command.id)));
    }

    @Test public void oneLetterDoesNotPolluteSearch() {
        assertTrue(SearchCommand.matching("w").isEmpty());
    }

    @Test public void helpRemainsLatentAndSearchable() {
        assertTrue(SearchCommand.matchingNormalized("help").stream()
                .anyMatch(command -> SearchCommand.HELP.equals(command.id)));
        assertTrue(SearchCommand.matchingNormalized("how to").stream()
                .anyMatch(command -> SearchCommand.HELP.equals(command.id)));
    }

    @Test public void preNormalizedCommandPathAvoidsRenormalizationContract() {
        assertTrue(SearchCommand.matchingNormalized("blu").stream()
                .anyMatch(command -> SearchCommand.BLUETOOTH.equals(command.id)));
        assertTrue(SearchCommand.matchingNormalized("x").isEmpty());
    }

    @Test public void recognizesSafeDialPayload() {
        assertEquals("+628123456789", QueryActions.dialPayload("+62 812-3456-789"));
        assertNull(QueryActions.dialPayload("12"));
        assertNull(QueryActions.dialPayload("call me"));
    }

    @Test public void recognizesWebDomains() {
        assertEquals("https://example.com", QueryActions.urlPayload("example.com"));
        assertEquals("https://example.com/a", QueryActions.urlPayload("https://example.com/a"));
        assertNull(QueryActions.urlPayload("not a url"));
    }
}
