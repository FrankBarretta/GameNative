package com.winlator.core.envvars

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for EnvVarInfo data class.
 * Tests environment variable information and known variable definitions.
 */
class EnvVarInfoTest {

    @Test
    fun testDataClass_initialization() {
        val envVar = EnvVarInfo(
            identifier = "TEST_VAR",
            selectionType = EnvVarSelectionType.TOGGLE,
            possibleValues = listOf("0", "1")
        )
        assertEquals("TEST_VAR", envVar.identifier)
        assertEquals(EnvVarSelectionType.TOGGLE, envVar.selectionType)
        assertEquals(listOf("0", "1"), envVar.possibleValues)
    }

    @Test
    fun testDataClass_defaultValues() {
        val envVar = EnvVarInfo(identifier = "TEST_VAR")
        assertEquals("TEST_VAR", envVar.identifier)
        assertEquals(EnvVarSelectionType.NONE, envVar.selectionType)
        assertEquals(emptyList<String>(), envVar.possibleValues)
    }

    @Test
    fun testDataClass_copy() {
        val original = EnvVarInfo("VAR1", EnvVarSelectionType.TOGGLE, listOf("0", "1"))
        val copy = original.copy(identifier = "VAR2")
        assertEquals("VAR2", copy.identifier)
        assertEquals(original.selectionType, copy.selectionType)
        assertEquals(original.possibleValues, copy.possibleValues)
    }

    @Test
    fun testKnownBox64Vars_containsExpectedKeys() {
        val knownVars = EnvVarInfo.KNOWN_BOX64_VARS
        assertTrue(knownVars.containsKey("BOX64_DYNAREC_SAFEFLAGS"))
        assertTrue(knownVars.containsKey("BOX64_DYNAREC_FASTNAN"))
        assertTrue(knownVars.containsKey("BOX64_DYNAREC_BIGBLOCK"))
        assertTrue(knownVars.containsKey("BOX64_AVX"))
        assertTrue(knownVars.containsKey("BOX64_MAXCPU"))
    }

    @Test
    fun testKnownBox64Vars_safeFlagsConfiguration() {
        val safeFlags = EnvVarInfo.KNOWN_BOX64_VARS["BOX64_DYNAREC_SAFEFLAGS"]
        assertNotNull(safeFlags)
        assertEquals("BOX64_DYNAREC_SAFEFLAGS", safeFlags!!.identifier)
        assertEquals(listOf("0", "1", "2"), safeFlags.possibleValues)
        assertEquals(EnvVarSelectionType.NONE, safeFlags.selectionType)
    }

    @Test
    fun testKnownBox64Vars_fastNanConfiguration() {
        val fastNan = EnvVarInfo.KNOWN_BOX64_VARS["BOX64_DYNAREC_FASTNAN"]
        assertNotNull(fastNan)
        assertEquals(EnvVarSelectionType.TOGGLE, fastNan!!.selectionType)
        assertEquals(listOf("0", "1"), fastNan.possibleValues)
    }

    @Test
    fun testKnownBox64Vars_maxCpuConfiguration() {
        val maxCpu = EnvVarInfo.KNOWN_BOX64_VARS["BOX64_MAXCPU"]
        assertNotNull(maxCpu)
        assertEquals(listOf("4", "8", "16", "32", "64"), maxCpu!!.possibleValues)
    }

    @Test
    fun testKnownFexCoreVars_containsExpectedKeys() {
        val knownVars = EnvVarInfo.KNOWN_FEXCORE_VARS
        assertTrue(knownVars.containsKey("FEX_TSOENABLED"))
        assertTrue(knownVars.containsKey("FEX_VECTORTSOENABLED"))
        assertTrue(knownVars.containsKey("FEX_MULTIBLOCK"))
        assertTrue(knownVars.containsKey("FEX_MAXINST"))
        assertTrue(knownVars.containsKey("FEX_HOSTFEATURES"))
    }

    @Test
    fun testKnownFexCoreVars_tsoEnabledConfiguration() {
        val tso = EnvVarInfo.KNOWN_FEXCORE_VARS["FEX_TSOENABLED"]
        assertNotNull(tso)
        assertEquals("FEX_TSOENABLED", tso!!.identifier)
        assertEquals(EnvVarSelectionType.TOGGLE, tso.selectionType)
        assertEquals(listOf("0", "1"), tso.possibleValues)
    }

    @Test
    fun testKnownFexCoreVars_hostFeaturesConfiguration() {
        val hostFeatures = EnvVarInfo.KNOWN_FEXCORE_VARS["FEX_HOSTFEATURES"]
        assertNotNull(hostFeatures)
        assertEquals(listOf("enablesve", "disablesve", "enableavx", "disableavx", "off"), hostFeatures!!.possibleValues)
    }

    @Test
    fun testKnownFexCoreVars_smcChecksConfiguration() {
        val smcChecks = EnvVarInfo.KNOWN_FEXCORE_VARS["FEX_SMC_CHECKS"]
        assertNotNull(smcChecks)
        assertEquals(listOf("none", "mtrack", "full"), smcChecks!!.possibleValues)
    }

    @Test
    fun testKnownEnvVars_containsExpectedKeys() {
        val knownVars = EnvVarInfo.KNOWN_ENV_VARS
        assertTrue(knownVars.containsKey("ZINK_DESCRIPTORS"))
        assertTrue(knownVars.containsKey("MESA_SHADER_CACHE_DISABLE"))
        assertTrue(knownVars.containsKey("WINEESYNC"))
        assertTrue(knownVars.containsKey("DXVK_HUD"))
        assertTrue(knownVars.containsKey("MESA_VK_WSI_PRESENT_MODE"))
    }

    @Test
    fun testKnownEnvVars_zinkDescriptorsConfiguration() {
        val zink = EnvVarInfo.KNOWN_ENV_VARS["ZINK_DESCRIPTORS"]
        assertNotNull(zink)
        assertEquals(listOf("auto", "lazy", "cached", "notemplates"), zink!!.possibleValues)
    }

    @Test
    fun testKnownEnvVars_mesaShaderCacheConfiguration() {
        val mesaCache = EnvVarInfo.KNOWN_ENV_VARS["MESA_SHADER_CACHE_DISABLE"]
        assertNotNull(mesaCache)
        assertEquals(EnvVarSelectionType.TOGGLE, mesaCache!!.selectionType)
        assertEquals(listOf("false", "true"), mesaCache.possibleValues)
    }

    @Test
    fun testKnownEnvVars_dxvkHudConfiguration() {
        val dxvkHud = EnvVarInfo.KNOWN_ENV_VARS["DXVK_HUD"]
        assertNotNull(dxvkHud)
        assertEquals(EnvVarSelectionType.MULTI_SELECT, dxvkHud!!.selectionType)
        assertTrue(dxvkHud.possibleValues.contains("fps"))
        assertTrue(dxvkHud.possibleValues.contains("memory"))
        assertTrue(dxvkHud.possibleValues.contains("devinfo"))
    }

    @Test
    fun testKnownEnvVars_tuDebugConfiguration() {
        val tuDebug = EnvVarInfo.KNOWN_ENV_VARS["TU_DEBUG"]
        assertNotNull(tuDebug)
        assertEquals(EnvVarSelectionType.MULTI_SELECT, tuDebug!!.selectionType)
        assertTrue(tuDebug.possibleValues.contains("noconform"))
        assertTrue(tuDebug.possibleValues.contains("perf"))
    }

    @Test
    fun testKnownEnvVars_presentModeConfiguration() {
        val presentMode = EnvVarInfo.KNOWN_ENV_VARS["MESA_VK_WSI_PRESENT_MODE"]
        assertNotNull(presentMode)
        assertEquals(listOf("immediate", "mailbox", "fifo", "relaxed"), presentMode!!.possibleValues)
    }

    @Test
    fun testAllKnownVars_haveValidIdentifiers() {
        EnvVarInfo.KNOWN_BOX64_VARS.forEach { (key, value) ->
            assertEquals(key, value.identifier)
        }
        EnvVarInfo.KNOWN_FEXCORE_VARS.forEach { (key, value) ->
            assertEquals(key, value.identifier)
        }
        EnvVarInfo.KNOWN_ENV_VARS.forEach { (key, value) ->
            assertEquals(key, value.identifier)
        }
    }

    @Test
    fun testKnownBox64Vars_countCheck() {
        // Verify we have all the BOX64 variables defined
        assertTrue(EnvVarInfo.KNOWN_BOX64_VARS.size >= 15)
    }

    @Test
    fun testKnownFexCoreVars_countCheck() {
        // Verify we have all the FEXCore variables defined
        assertTrue(EnvVarInfo.KNOWN_FEXCORE_VARS.size >= 14)
    }

    @Test
    fun testKnownEnvVars_countCheck() {
        // Verify we have a good set of environment variables
        assertTrue(EnvVarInfo.KNOWN_ENV_VARS.size >= 10)
    }

    @Test
    fun testEnvVarInfo_emptyPossibleValues() {
        val envVar = EnvVarInfo("CUSTOM_VAR", EnvVarSelectionType.NONE, emptyList())
        assertTrue(envVar.possibleValues.isEmpty())
    }

    @Test
    fun testEnvVarInfo_multipleValuesToggle() {
        val envVar = EnvVarInfo("TEST", EnvVarSelectionType.TOGGLE, listOf("0", "1"))
        assertEquals(2, envVar.possibleValues.size)
        assertEquals(EnvVarSelectionType.TOGGLE, envVar.selectionType)
    }

    @Test
    fun testKnownBox64Vars_weakBarrierValues() {
        val weakBarrier = EnvVarInfo.KNOWN_BOX64_VARS["BOX64_DYNAREC_WEAKBARRIER"]
        assertNotNull(weakBarrier)
        assertEquals(listOf("0", "1", "2"), weakBarrier!!.possibleValues)
    }

    @Test
    fun testKnownBox64Vars_bigBlockValues() {
        val bigBlock = EnvVarInfo.KNOWN_BOX64_VARS["BOX64_DYNAREC_BIGBLOCK"]
        assertNotNull(bigBlock)
        assertEquals(listOf("0", "1", "2", "3"), bigBlock!!.possibleValues)
    }

    @Test
    fun testKnownFexCoreVars_allToggleSwitches() {
        val toggleVars = listOf(
            "FEX_TSOENABLED",
            "FEX_VECTORTSOENABLED",
            "FEX_HALFBARRIERTSOENABLED",
            "FEX_MEMCPYSETTSOENABLED",
            "FEX_X87REDUCEDPRECISION",
            "FEX_MULTIBLOCK"
        )
        
        toggleVars.forEach { varName ->
            val envVar = EnvVarInfo.KNOWN_FEXCORE_VARS[varName]
            assertNotNull("$varName should exist", envVar)
            assertEquals("$varName should be TOGGLE type", EnvVarSelectionType.TOGGLE, envVar!!.selectionType)
            assertEquals("$varName should have binary values", listOf("0", "1"), envVar.possibleValues)
        }
    }

    @Test
    fun testDataClass_equality() {
        val var1 = EnvVarInfo("TEST", EnvVarSelectionType.TOGGLE, listOf("0", "1"))
        val var2 = EnvVarInfo("TEST", EnvVarSelectionType.TOGGLE, listOf("0", "1"))
        assertEquals(var1, var2)
    }

    @Test
    fun testDataClass_hashCode() {
        val var1 = EnvVarInfo("TEST", EnvVarSelectionType.TOGGLE, listOf("0", "1"))
        val var2 = EnvVarInfo("TEST", EnvVarSelectionType.TOGGLE, listOf("0", "1"))
        assertEquals(var1.hashCode(), var2.hashCode())
    }
}