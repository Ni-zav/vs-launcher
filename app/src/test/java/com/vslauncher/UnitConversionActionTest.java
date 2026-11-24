package com.vslauncher;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UnitConversionActionTest {
    @Test
    public void convertsLengthMassVolumeSpeedAndTemperature() {
        UnitConversionAction.Conversion kmToMi = UnitConversionAction.evaluate("10km to mi");
        assertNotNull(kmToMi);
        assertEquals("10 km = 6.21 mi", kmToMi.display);
        assertEquals("6.21 mi", kmToMi.copyValue);

        UnitConversionAction.Conversion fToC = UnitConversionAction.evaluate("72f to c");
        assertNotNull(fToC);
        assertEquals("72 F = 22.22 C", fToC.display);

        UnitConversionAction.Conversion cToF = UnitConversionAction.evaluate("0 c in f");
        assertNotNull(cToF);
        assertEquals("0 C = 32 F", cToF.display);

        UnitConversionAction.Conversion kgToLb = UnitConversionAction.evaluate("2.5 kg -> lb");
        assertNotNull(kgToLb);
        assertEquals("2.5 kg = 5.51 lb", kgToLb.display);

        UnitConversionAction.Conversion mphToKph = UnitConversionAction.evaluate("60mph to kph");
        assertNotNull(mphToKph);
        assertEquals("60 mph = 96.56 km/h", mphToKph.display);
    }

    @Test
    public void rejectsInvalidOrMismatchedUnits() {
        assertNull(UnitConversionAction.evaluate("10km to kg"));
        assertNull(UnitConversionAction.evaluate("10km to km"));
        assertNull(UnitConversionAction.evaluate("hello world"));
        assertNull(UnitConversionAction.evaluate("10km mi"));
        assertNull(UnitConversionAction.evaluate(""));
        assertNull(UnitConversionAction.evaluate(null));
    }

    @Test
    public void conversionSearchResultBlocksAutoLaunch() {
        UnitConversionAction.Conversion conv = UnitConversionAction.evaluate("100cm to m");
        assertNotNull(conv);
        SearchResult result = SearchResult.conversion(conv);
        assertEquals(SearchResult.TYPE_CONVERSION, result.type);
        assertEquals("CONVERT", result.meta);
        assertTrue(result.blocksAppAutoLaunch());
    }

    @Test
    public void torchCommandMatchesFlashlightKeywords() {
        List<SearchCommand> matches = SearchCommand.matching("torch");
        assertTrue(matches.stream().anyMatch(c -> SearchCommand.TORCH.equals(c.id)));
        List<SearchCommand> flashMatches = SearchCommand.matching("flash");
        assertTrue(flashMatches.stream().anyMatch(c -> SearchCommand.TORCH.equals(c.id)));
    }

    @Test
    public void matchSpanFindsContiguousMatch() {
        assertArrayEquals(new int[] { 0, 3 }, SearchRanking.matchSpan("Calendar", "cal"));
        assertArrayEquals(new int[] { 7, 11 }, SearchRanking.matchSpan("Google Maps", "maps"));
        assertNull(SearchRanking.matchSpan("WhatsApp", "wa"));
        assertNull(SearchRanking.matchSpan("Calendar", ""));
    }
}
