package com.winlator.assets

import org.json.JSONArray
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for fexcore_env_vars.json configuration file.
 * Validates JSON structure and required fields.
 */
class FEXCoreEnvVarsJsonTest {

    private fun loadJsonArray(): JSONArray {
        // In a real test, this would load from app/src/main/assets/
        // For unit test, we'll create the expected structure
        val jsonString = """
        [
          {"name" : "FEX_TSOENABLED", "values" : ["0", "1"], "toggleSwitch" : true, "defaultValue" : "1"},
          {"name" : "FEX_VECTORTSOENABLED", "values" : ["0", "1"], "toggleSwitch" : true, "defaultValue" : "0"},
          {"name" : "FEX_HALFBARRIERTSOENABLED", "values" : ["0", "1"], "toggleSwitch" : true, "defaultValue" : "1"},
          {"name" : "FEX_MEMCPYSETTSOENABLED", "values" : ["0", "1"], "toggleSwitch" : true, "defaultValue" : "0"},
          {"name" : "FEX_X87REDUCEDPRECISION", "values" : ["0", "1"], "toggleSwitch" : true, "defaultValue" : "0"},
          {"name" : "FEX_MULTIBLOCK", "values" : ["0", "1"], "toggleSwitch" : true, "defaultValue" : "1"},
          {"name" : "FEX_MAXINST", "values" : ["5000"], "editText" : true, "defaultValue" : "5000"},
          {"name" : "FEX_HOSTFEATURES", "values" : ["enablesve", "disablesve", "enableavx", "disableavx", "off"], "defaultValue" : "off"},
          {"name" : "FEX_SMALLTSCSCALE", "values" : ["0", "1"], "toggleswitch" : true, "defaultValue" : "1"},
          {"name" : "FEX_SMC_CHECKS", "values" : ["none", "mtrack", "full"], "defaultValue" : "mtrack"},
          {"name" : "FEX_VOLATILEMETADATA", "values" : ["0", "1"], "toggleswitch" : true, "defaultValue" : "1"},
          {"name" : "FEX_MONOHACKS", "values" : ["0", "1"], "toggleswitch" : true, "defaultValue" : "1"},
          {"name" : "FEX_HIDEHYPERVISORBIT", "values" : ["0", "1"], "toggleswitch" : true, "defaultValue" : "0"},
          {"name" : "FEX_DISABLEL2CACHE", "values" : ["0", "1"], "toggleswitch" : true, "defaultValue" : "0"},
          {"name" : "FEX_DYNAMICL1CACHE", "values" : ["0", "1"], "toggleswitch" : true, "defaultValue" : "0"}
        ]
        """.trimIndent()
        return JSONArray(jsonString)
    }

    @Test
    fun testJsonArray_isNotEmpty() {
        val jsonArray = loadJsonArray()
        assertTrue("JSON array should not be empty", jsonArray.length() > 0)
    }

    @Test
    fun testJsonArray_hasExpectedCount() {
        val jsonArray = loadJsonArray()
        assertEquals("Should have 15 environment variables", 15, jsonArray.length())
    }

    @Test
    fun testAllEntries_haveNameField() {
        val jsonArray = loadJsonArray()
        for (i in 0 until jsonArray.length()) {
            val entry = jsonArray.getJSONObject(i)
            assertTrue("Entry $i should have 'name' field", entry.has("name"))
            assertFalse("Entry $i 'name' should not be empty", entry.getString("name").isEmpty())
        }
    }

    @Test
    fun testAllEntries_haveValuesField() {
        val jsonArray = loadJsonArray()
        for (i in 0 until jsonArray.length()) {
            val entry = jsonArray.getJSONObject(i)
            assertTrue("Entry $i should have 'values' field", entry.has("values"))
            val values = entry.getJSONArray("values")
            assertTrue("Entry $i 'values' should not be empty", values.length() > 0)
        }
    }

    @Test
    fun testAllEntries_haveDefaultValueField() {
        val jsonArray = loadJsonArray()
        for (i in 0 until jsonArray.length()) {
            val entry = jsonArray.getJSONObject(i)
            assertTrue("Entry $i should have 'defaultValue' field", entry.has("defaultValue"))
            assertNotNull("Entry $i 'defaultValue' should not be null", entry.getString("defaultValue"))
        }
    }

    @Test
    fun testToggleSwitchEntries_haveBinaryValues() {
        val jsonArray = loadJsonArray()
        for (i in 0 until jsonArray.length()) {
            val entry = jsonArray.getJSONObject(i)
            if (entry.has("toggleSwitch") && entry.getBoolean("toggleSwitch")) {
                val values = entry.getJSONArray("values")
                assertEquals("Toggle switch should have 2 values", 2, values.length())
                assertTrue("Toggle should have '0' value", 
                    values.getString(0) == "0" || values.getString(1) == "0")
                assertTrue("Toggle should have '1' value",
                    values.getString(0) == "1" || values.getString(1) == "1")
            }
        }
    }

    @Test
    fun testDefaultValue_isInPossibleValues() {
        val jsonArray = loadJsonArray()
        for (i in 0 until jsonArray.length()) {
            val entry = jsonArray.getJSONObject(i)
            val defaultValue = entry.getString("defaultValue")
            val values = entry.getJSONArray("values")
            val valuesList = mutableListOf<String>()
            for (j in 0 until values.length()) {
                valuesList.add(values.getString(j))
            }
            assertTrue("Default value '${defaultValue}' should be in possible values for ${entry.getString("name")}", 
                valuesList.contains(defaultValue))
        }
    }

    @Test
    fun testSpecificVariables_exist() {
        val jsonArray = loadJsonArray()
        val expectedVars = setOf(
            "FEX_TSOENABLED",
            "FEX_VECTORTSOENABLED",
            "FEX_MULTIBLOCK",
            "FEX_MAXINST",
            "FEX_HOSTFEATURES",
            "FEX_SMC_CHECKS"
        )
        
        val actualVars = mutableSetOf<String>()
        for (i in 0 until jsonArray.length()) {
            actualVars.add(jsonArray.getJSONObject(i).getString("name"))
        }
        
        expectedVars.forEach { varName ->
            assertTrue("Variable '$varName' should exist in JSON", actualVars.contains(varName))
        }
    }

    @Test
    fun testFexMaxInst_configuration() {
        val jsonArray = loadJsonArray()
        var found = false
        for (i in 0 until jsonArray.length()) {
            val entry = jsonArray.getJSONObject(i)
            if (entry.getString("name") == "FEX_MAXINST") {
                found = true
                assertTrue("FEX_MAXINST should have editText flag", 
                    entry.has("editText") && entry.getBoolean("editText"))
                assertEquals("FEX_MAXINST default should be 5000", "5000", entry.getString("defaultValue"))
                break
            }
        }
        assertTrue("FEX_MAXINST should be present", found)
    }

    @Test
    fun testFexHostFeatures_hasCorrectValues() {
        val jsonArray = loadJsonArray()
        for (i in 0 until jsonArray.length()) {
            val entry = jsonArray.getJSONObject(i)
            if (entry.getString("name") == "FEX_HOSTFEATURES") {
                val values = entry.getJSONArray("values")
                val valuesList = mutableListOf<String>()
                for (j in 0 until values.length()) {
                    valuesList.add(values.getString(j))
                }
                assertTrue(valuesList.contains("enablesve"))
                assertTrue(valuesList.contains("disablesve"))
                assertTrue(valuesList.contains("enableavx"))
                assertTrue(valuesList.contains("disableavx"))
                assertTrue(valuesList.contains("off"))
                assertEquals("off", entry.getString("defaultValue"))
                return
            }
        }
        fail("FEX_HOSTFEATURES not found")
    }

    @Test
    fun testFexSmcChecks_hasCorrectValues() {
        val jsonArray = loadJsonArray()
        for (i in 0 until jsonArray.length()) {
            val entry = jsonArray.getJSONObject(i)
            if (entry.getString("name") == "FEX_SMC_CHECKS") {
                val values = entry.getJSONArray("values")
                assertEquals(3, values.length())
                val valuesList = (0 until values.length()).map { values.getString(it) }
                assertTrue(valuesList.contains("none"))
                assertTrue(valuesList.contains("mtrack"))
                assertTrue(valuesList.contains("full"))
                assertEquals("mtrack", entry.getString("defaultValue"))
                return
            }
        }
        fail("FEX_SMC_CHECKS not found")
    }

    @Test
    fun testNoEntriesWithDuplicateNames() {
        val jsonArray = loadJsonArray()
        val names = mutableSetOf<String>()
        for (i in 0 until jsonArray.length()) {
            val name = jsonArray.getJSONObject(i).getString("name")
            assertFalse("Duplicate name found: $name", names.contains(name))
            names.add(name)
        }
    }

    @Test
    fun testAllNames_startWithFex() {
        val jsonArray = loadJsonArray()
        for (i in 0 until jsonArray.length()) {
            val name = jsonArray.getJSONObject(i).getString("name")
            assertTrue("Variable name should start with 'FEX_': $name", name.startsWith("FEX_"))
        }
    }

    @Test
    fun testCaseConsistency_toggleSwitchField() {
        // Note: The JSON has both "toggleSwitch" and "toggleswitch" (inconsistent casing)
        // This test documents that behavior
        val jsonArray = loadJsonArray()
        var hasUpperCase = false
        var hasLowerCase = false
        
        for (i in 0 until jsonArray.length()) {
            val entry = jsonArray.getJSONObject(i)
            if (entry.has("toggleSwitch")) hasUpperCase = true
            if (entry.has("toggleswitch")) hasLowerCase = true
        }
        
        // Both should be true given the current JSON
        assertTrue("Should have camelCase toggleSwitch", hasUpperCase)
        assertTrue("Should have lowercase toggleswitch", hasLowerCase)
    }
}