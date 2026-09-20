package com.vslauncher;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SearchNormalizationTest {
    @Test public void stripsAccents() {
        assertEquals("pokemon", SearchNormalization.normalize("Pokémon"));
        assertEquals("resume", SearchNormalization.normalize("Résumé"));
    }

    @Test public void collapsesPunctuationAndWhitespace() {
        assertEquals("my app", SearchNormalization.normalize("  my---app  "));
        assertEquals("youtube music", SearchNormalization.normalize("YouTube   Music"));
    }

    @Test public void keepsDigits() {
        assertEquals("1password", SearchNormalization.normalize("1Password"));
        assertEquals("2fas auth", SearchNormalization.normalize("2FAS Auth"));
    }

    @Test public void handlesEmptyInput() {
        assertEquals("", SearchNormalization.normalize(null));
        assertEquals("", SearchNormalization.normalize(""));
    }
}
