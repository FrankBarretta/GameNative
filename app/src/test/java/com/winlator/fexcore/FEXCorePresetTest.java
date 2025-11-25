package com.winlator.fexcore;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for FEXCorePreset class.
 * Tests the preset model and its methods.
 */
public class FEXCorePresetTest {

    @Test
    public void testConstructor_withValidParameters() {
        FEXCorePreset preset = new FEXCorePreset("STABILITY", "Stability Mode");
        assertEquals("STABILITY", preset.id);
        assertEquals("Stability Mode", preset.name);
    }

    @Test
    public void testConstructor_withEmptyStrings() {
        FEXCorePreset preset = new FEXCorePreset("", "");
        assertEquals("", preset.id);
        assertEquals("", preset.name);
    }

    @Test
    public void testIsCustom_withStandardPreset() {
        FEXCorePreset preset = new FEXCorePreset(FEXCorePreset.STABILITY, "Stability");
        assertFalse(preset.isCustom());
    }

    @Test
    public void testIsCustom_withCompatibilityPreset() {
        FEXCorePreset preset = new FEXCorePreset(FEXCorePreset.COMPATIBILITY, "Compatibility");
        assertFalse(preset.isCustom());
    }

    @Test
    public void testIsCustom_withIntermediatePreset() {
        FEXCorePreset preset = new FEXCorePreset(FEXCorePreset.INTERMEDIATE, "Intermediate");
        assertFalse(preset.isCustom());
    }

    @Test
    public void testIsCustom_withPerformancePreset() {
        FEXCorePreset preset = new FEXCorePreset(FEXCorePreset.PERFORMANCE, "Performance");
        assertFalse(preset.isCustom());
    }

    @Test
    public void testIsCustom_withCustomPreset() {
        FEXCorePreset preset = new FEXCorePreset("CUSTOM-1", "My Custom Preset");
        assertTrue(preset.isCustom());
    }

    @Test
    public void testIsCustom_withCustomPrefixOnly() {
        FEXCorePreset preset = new FEXCorePreset("CUSTOM", "Custom");
        assertTrue(preset.isCustom());
    }

    @Test
    public void testIsCustom_withCustomPrefix() {
        FEXCorePreset preset = new FEXCorePreset("CUSTOM-123", "Custom 123");
        assertTrue(preset.isCustom());
    }

    @Test
    public void testIsCustom_withDifferentCase() {
        FEXCorePreset preset = new FEXCorePreset("custom-1", "Custom");
        assertFalse(preset.isCustom()); // Should be case-sensitive
    }

    @Test
    public void testToString_returnsName() {
        FEXCorePreset preset = new FEXCorePreset("STABILITY", "Stability Mode");
        assertEquals("Stability Mode", preset.toString());
    }

    @Test
    public void testToString_withEmptyName() {
        FEXCorePreset preset = new FEXCorePreset("ID", "");
        assertEquals("", preset.toString());
    }

    @Test
    public void testToString_withSpecialCharacters() {
        FEXCorePreset preset = new FEXCorePreset("ID", "Test (1) - Special");
        assertEquals("Test (1) - Special", preset.toString());
    }

    @Test
    public void testPresetConstants_areCorrect() {
        assertEquals("STABILITY", FEXCorePreset.STABILITY);
        assertEquals("COMPATIBILITY", FEXCorePreset.COMPATIBILITY);
        assertEquals("INTERMEDIATE", FEXCorePreset.INTERMEDIATE);
        assertEquals("PERFORMANCE", FEXCorePreset.PERFORMANCE);
        assertEquals("CUSTOM", FEXCorePreset.CUSTOM);
    }

    @Test
    public void testIsCustom_withNullId() {
        FEXCorePreset preset = new FEXCorePreset(null, "Name");
        try {
            preset.isCustom();
            fail("Should throw NullPointerException");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    @Test
    public void testIsCustom_edgeCaseWithCustomInMiddle() {
        FEXCorePreset preset = new FEXCorePreset("PRE-CUSTOM-POST", "Test");
        assertFalse(preset.isCustom()); // startsWith should return false
    }

    @Test
    public void testEquality_sameValues() {
        FEXCorePreset preset1 = new FEXCorePreset("STABILITY", "Stability");
        FEXCorePreset preset2 = new FEXCorePreset("STABILITY", "Stability");
        // Note: Without overriding equals(), these will not be equal
        assertNotSame(preset1, preset2);
    }
}