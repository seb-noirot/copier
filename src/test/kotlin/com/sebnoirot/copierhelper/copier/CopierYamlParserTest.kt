package com.sebnoirot.copierhelper.copier

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CopierYamlParserTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun testParseYamlWithRootLevelVariables() {
        // Create a temporary YAML file with the example content
        val yamlContent = """
            sport:
              type: str
              help: What sport you want to generate?
            version:
              type: str
              help: Version of the template
              default: 0.0.7
            _subdirectory: template
        """.trimIndent()
        
        val yamlFile = tempFolder.newFile("copier.yaml")
        yamlFile.writeText(yamlContent)
        
        // Parse the YAML file
        val variables = CopierYamlParser.parseYaml(yamlFile)
        
        // Verify the results
        assertEquals(2, variables.size)
        
        // Check the "sport" variable
        val sportVar = variables["sport"]
        assertNotNull(sportVar)
        assertEquals("sport", sportVar?.name)
        assertEquals("str", sportVar?.type)
        assertEquals("", sportVar?.defaultValue)
        assertEquals("What sport you want to generate?", sportVar?.help)
        assertTrue(sportVar?.choices?.isEmpty() ?: false)
        
        // Check the "version" variable
        val versionVar = variables["version"]
        assertNotNull(versionVar)
        assertEquals("version", versionVar?.name)
        assertEquals("str", versionVar?.type)
        assertEquals("0.0.7", versionVar?.defaultValue)
        assertEquals("Version of the template", versionVar?.help)
        assertTrue(versionVar?.choices?.isEmpty() ?: false)
        
        // Verify that _subdirectory was not parsed as a variable
        assertNull(variables["_subdirectory"])
    }
}