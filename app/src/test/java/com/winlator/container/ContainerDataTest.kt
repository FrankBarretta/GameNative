package com.winlator.container

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for ContainerData data class.
 * Tests container configuration data model.
 */
class ContainerDataTest {

    @Test
    fun testDefaultValues() {
        val data = ContainerData()
        assertEquals("", data.name)
        assertEquals(Container.DEFAULT_SCREEN_SIZE, data.screenSize)
        assertEquals(Container.DEFAULT_ENV_VARS, data.envVars)
        assertEquals(Container.DEFAULT_GRAPHICS_DRIVER, data.graphicsDriver)
        assertEquals("", data.graphicsDriverVersion)
        assertEquals(Container.DEFAULT_DXWRAPPER, data.dxwrapper)
        assertEquals(Container.DEFAULT_AUDIO_DRIVER, data.audioDriver)
        assertFalse(data.showFPS)
        assertFalse(data.launchRealSteam)
        assertTrue(data.wow64Mode)
    }

    @Test
    fun testCopyWithModifications() {
        val original = ContainerData(name = "Test Container", showFPS = true)
        val modified = original.copy(screenSize = "1920x1080")
        
        assertEquals("Test Container", modified.name)
        assertEquals("1920x1080", modified.screenSize)
        assertTrue(modified.showFPS)
    }

    @Test
    fun testSharpnessSettings_defaults() {
        val data = ContainerData()
        assertEquals("None", data.sharpnessEffect)
        assertEquals(100, data.sharpnessLevel)
        assertEquals(100, data.sharpnessDenoise)
    }

    @Test
    fun testSharpnessSettings_customValues() {
        val data = ContainerData(
            sharpnessEffect = "CAS",
            sharpnessLevel = 75,
            sharpnessDenoise = 50
        )
        assertEquals("CAS", data.sharpnessEffect)
        assertEquals(75, data.sharpnessLevel)
        assertEquals(50, data.sharpnessDenoise)
    }

    @Test
    fun testSharpnessLevel_boundaryValues() {
        val data1 = ContainerData(sharpnessLevel = 0)
        assertEquals(0, data1.sharpnessLevel)
        
        val data2 = ContainerData(sharpnessLevel = 100)
        assertEquals(100, data2.sharpnessLevel)
    }

    @Test
    fun testGraphicsDriverConfig_initialization() {
        val data = ContainerData(graphicsDriverConfig = "version=1.0;maxMemory=4096")
        assertEquals("version=1.0;maxMemory=4096", data.graphicsDriverConfig)
    }

    @Test
    fun testContainerVariant_defaults() {
        val data = ContainerData()
        assertEquals(Container.DEFAULT_VARIANT, data.containerVariant)
    }

    @Test
    fun testEmulator_defaults() {
        val data = ContainerData()
        assertEquals(Container.DEFAULT_EMULATOR, data.emulator)
    }

    @Test
    fun testCpuList_defaults() {
        val data = ContainerData()
        assertEquals(Container.getFallbackCPUList(), data.cpuList)
        assertEquals(Container.getFallbackCPUListWoW64(), data.cpuListWoW64)
    }

    @Test
    fun testStartupSelection_defaults() {
        val data = ContainerData()
        assertEquals(Container.STARTUP_SELECTION_ESSENTIAL, data.startupSelection)
    }

    @Test
    fun testSteamType_defaults() {
        val data = ContainerData()
        assertEquals("normal", data.steamType)
    }

    @Test
    fun testInputSettings_defaults() {
        val data = ContainerData()
        assertTrue(data.sdlControllerAPI)
        assertTrue(data.enableXInput)
        assertTrue(data.enableDInput)
        assertEquals(1.toByte(), data.dinputMapperType)
        assertFalse(data.disableMouseInput)
        assertFalse(data.touchscreenMode)
    }

    @Test
    fun testLanguage_defaults() {
        val data = ContainerData()
        assertEquals("english", data.language)
    }

    @Test
    fun testControllerEmulation_defaults() {
        val data = ContainerData()
        assertFalse(data.emulateKeyboardMouse)
        assertEquals("", data.controllerEmulationBindings)
    }

    @Test
    fun testDlcAndDrmSettings_defaults() {
        val data = ContainerData()
        assertFalse(data.forceDlc)
        assertFalse(data.useLegacyDRM)
    }

    @Test
    fun testUseDRI3_defaults() {
        val data = ContainerData()
        assertTrue(data.useDRI3)
    }

    @Test
    fun testWineRegistrySettings_defaults() {
        val data = ContainerData()
        assertEquals("gl", data.renderer)
        assertTrue(data.csmt)
        assertEquals(1728, data.videoPciDeviceID)
        assertEquals("fbo", data.offScreenRenderingMode)
        assertTrue(data.strictShaderMath)
        assertEquals("2048", data.videoMemorySize)
    }

    @Test
    fun testShaderSettings_defaults() {
        val data = ContainerData()
        assertEquals("glsl", data.shaderBackend)
        assertEquals("enabled", data.useGLSL)
    }

    @Test
    fun testMouseWarp_defaults() {
        val data = ContainerData()
        assertEquals("disable", data.mouseWarpOverride)
    }

    @Test
    fun testDrives_defaults() {
        val data = ContainerData()
        assertEquals(Container.DEFAULT_DRIVES, data.drives)
    }

    @Test
    fun testExecutionSettings_defaults() {
        val data = ContainerData()
        assertEquals("", data.execArgs)
        assertEquals("", data.executablePath)
        assertEquals("", data.installPath)
    }

    @Test
    fun testWinComponents_defaults() {
        val data = ContainerData()
        assertEquals(Container.DEFAULT_WINCOMPONENTS, data.wincomponents)
    }

    @Test
    fun testCopy_allFields() {
        val original = ContainerData(
            name = "Container1",
            screenSize = "1920x1080",
            showFPS = true,
            sharpnessEffect = "FSR",
            sharpnessLevel = 80,
            sharpnessDenoise = 60
        )
        
        val copy = original.copy()
        assertEquals(original.name, copy.name)
        assertEquals(original.screenSize, copy.screenSize)
        assertEquals(original.showFPS, copy.showFPS)
        assertEquals(original.sharpnessEffect, copy.sharpnessEffect)
        assertEquals(original.sharpnessLevel, copy.sharpnessLevel)
        assertEquals(original.sharpnessDenoise, copy.sharpnessDenoise)
    }

    @Test
    fun testEquality() {
        val data1 = ContainerData(name = "Test", sharpnessLevel = 75)
        val data2 = ContainerData(name = "Test", sharpnessLevel = 75)
        assertEquals(data1, data2)
    }

    @Test
    fun testHashCode() {
        val data1 = ContainerData(name = "Test", sharpnessLevel = 75)
        val data2 = ContainerData(name = "Test", sharpnessLevel = 75)
        assertEquals(data1.hashCode(), data2.hashCode())
    }

    @Test
    fun testInequality_differentName() {
        val data1 = ContainerData(name = "Test1")
        val data2 = ContainerData(name = "Test2")
        assertNotEquals(data1, data2)
    }

    @Test
    fun testInequality_differentSharpness() {
        val data1 = ContainerData(sharpnessLevel = 50)
        val data2 = ContainerData(sharpnessLevel = 75)
        assertNotEquals(data1, data2)
    }

    @Test
    fun testSharpnessLevel_edgeCases() {
        val negativeData = ContainerData(sharpnessLevel = -10)
        assertEquals(-10, negativeData.sharpnessLevel) // Should allow negative if no validation
        
        val highData = ContainerData(sharpnessLevel = 150)
        assertEquals(150, highData.sharpnessLevel) // Should allow over 100 if no validation
    }

    @Test
    fun testSharpnessDenoise_edgeCases() {
        val zeroData = ContainerData(sharpnessDenoise = 0)
        assertEquals(0, zeroData.sharpnessDenoise)
        
        val maxData = ContainerData(sharpnessDenoise = 100)
        assertEquals(100, maxData.sharpnessDenoise)
    }

    @Test
    fun testAllowSteamUpdates_combination() {
        val data = ContainerData(launchRealSteam = true, allowSteamUpdates = true)
        assertTrue(data.launchRealSteam)
        assertTrue(data.allowSteamUpdates)
    }

    @Test
    fun testWow64Mode_withCpuLists() {
        val data = ContainerData(
            wow64Mode = true,
            cpuList = "0,1,2,3",
            cpuListWoW64 = "0,1"
        )
        assertTrue(data.wow64Mode)
        assertEquals("0,1,2,3", data.cpuList)
        assertEquals("0,1", data.cpuListWoW64)
    }

    @Test
    fun testDinputMapperType_values() {
        val standard = ContainerData(dinputMapperType = 1)
        assertEquals(1.toByte(), standard.dinputMapperType)
        
        val xinput = ContainerData(dinputMapperType = 2)
        assertEquals(2.toByte(), xinput.dinputMapperType)
    }

    @Test
    fun testEmptyStringFields() {
        val data = ContainerData(
            name = "",
            graphicsDriverVersion = "",
            graphicsDriverConfig = "",
            execArgs = "",
            executablePath = ""
        )
        assertEquals("", data.name)
        assertEquals("", data.graphicsDriverVersion)
        assertEquals("", data.graphicsDriverConfig)
        assertEquals("", data.execArgs)
        assertEquals("", data.executablePath)
    }
}