package org.dce.ed.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class MiningSheetTitlesTest {

    @Test
    void sheetTitleForCommander_usesCmdrPrefix() {
        assertEquals("CMDR -", MiningSheetTitles.sheetTitleForCommander(null));
        assertEquals("CMDR -", MiningSheetTitles.sheetTitleForCommander("   "));
        assertEquals("CMDR Villunus", MiningSheetTitles.sheetTitleForCommander("Villunus"));
    }

    @Test
    void isCmdrMiningWorksheet_recognizesPrefixCaseInsensitive() {
        assertTrue(MiningSheetTitles.isCmdrMiningWorksheet("CMDR Villunus"));
        assertTrue(MiningSheetTitles.isCmdrMiningWorksheet("  cmdr UkeBard  "));
        assertFalse(MiningSheetTitles.isCmdrMiningWorksheet("Sheet1"));
        assertFalse(MiningSheetTitles.isCmdrMiningWorksheet("Prospector log"));
        assertFalse(MiningSheetTitles.isCmdrMiningWorksheet("Villunus"));
    }

    @Test
    void commanderNameFromCmdrWorksheetTitle_stripsPrefix() {
        assertEquals("Villunus", MiningSheetTitles.commanderNameFromCmdrWorksheetTitle("CMDR Villunus"));
        assertEquals("Uke Bard", MiningSheetTitles.commanderNameFromCmdrWorksheetTitle("cmdr Uke Bard"));
        assertEquals("", MiningSheetTitles.commanderNameFromCmdrWorksheetTitle("Sheet1"));
    }

    @Test
    void sanitizeTitle_replacesInvalidCharacters() {
        assertEquals("a_b_c", MiningSheetTitles.sanitizeTitle("a:b*c"));
        assertEquals("x_y", MiningSheetTitles.sanitizeTitle("x\\y"));
        assertEquals("q_r", MiningSheetTitles.sanitizeTitle("q?r"));
    }

    @Test
    void sanitizeTitle_collapsesWhitespace() {
        assertEquals("Cmdr Name", MiningSheetTitles.sanitizeTitle("Cmdr   Name"));
    }

    @Test
    void uniqueTitle_addsNumericSuffixOnCollision() {
        Set<String> used = new LinkedHashSet<>();
        used.add("CMDR Villunus");
        assertEquals("CMDR Villunus (2)", MiningSheetTitles.uniqueTitle("CMDR Villunus", used));
    }

    @Test
    void quoteSheetNameForRange_escapesSingleQuotes() {
        assertEquals("'It''s fine'", MiningSheetTitles.quoteSheetNameForRange("It's fine"));
    }

    @Test
    void rangeA1P_prefixesQuotedName() {
        assertTrue(MiningSheetTitles.rangeA1P("Sheet1").startsWith("'"));
        assertTrue(MiningSheetTitles.rangeA1P("Sheet1").endsWith("!A:Q"));
    }

    @Test
    void sheetTitleForCommander_truncatesVeryLongNameToApiLimit() {
        String longName = "X".repeat(120);
        String title = MiningSheetTitles.sheetTitleForCommander(longName);
        assertTrue(title.length() <= 99);
        assertTrue(title.startsWith(MiningSheetTitles.CMDR_WORKSHEET_PREFIX));
    }

    @Test
    void sanitizeTitle_invalidCharsBecomeUnderscores() {
        assertEquals("____", MiningSheetTitles.sanitizeTitle("::::"));
    }

    @Test
    void uniqueTitle_severalCollisions_findsNextFreeSuffix() {
        Set<String> used = new LinkedHashSet<>();
        used.add("CMDR Test");
        used.add("CMDR Test (2)");
        used.add("CMDR Test (3)");
        assertEquals("CMDR Test (4)", MiningSheetTitles.uniqueTitle("CMDR Test", used));
    }

    @Test
    void commanderNameFromCmdrWorksheetTitle_requiresCmdrPrefixWithSpace() {
        assertEquals("", MiningSheetTitles.commanderNameFromCmdrWorksheetTitle("CMDR"));
        assertEquals("X", MiningSheetTitles.commanderNameFromCmdrWorksheetTitle("CMDR X"));
    }

    @Test
    void isCmdrMiningWorksheet_requiresSpaceAfterCmdrLetters() {
        assertFalse(MiningSheetTitles.isCmdrMiningWorksheet("CMDRX"));
    }

    @Test
    void quoteSheetNameForRange_nullTreatedAsEmpty() {
        assertEquals("''", MiningSheetTitles.quoteSheetNameForRange(null));
    }
}
