package com.antigravity.studio.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AntigravityModelTest {

    @Before
    fun setUp() {
        AntigravityModelCatalog.selectModel(AntigravityModelCatalog.defaultModel)
    }

    @Test
    fun testCatalogHasSevenOfficialModels() {
        val models = AntigravityModelCatalog.models
        assertEquals(7, models.size)
        assertEquals("gemini-3.8-flash-high", models[0].id)
        assertEquals("Gemini 3.8 Flash", models[0].displayName)
        assertEquals(listOf("High", "Fast"), models[0].tags)
        assertFalse(models[0].isThinking)

        assertEquals("gemini-3.7-flash-high", models[1].id)
        assertEquals("gemini-3.6-flash-high", models[2].id)
        assertEquals("gemini-3.1-pro-low", models[3].id)

        assertEquals("claude-sonnet-4-6", models[4].id)
        assertEquals("Claude Sonnet 4.6 (Thinking)", models[4].displayName)
        assertTrue(models[4].isThinking)

        assertEquals("claude-opus-4-6-thinking", models[5].id)
        assertEquals("Claude Opus 4.6 (Thinking)", models[5].displayName)
        assertTrue(models[5].isThinking)

        assertEquals("gpt-oss-120b-medium", models[6].id)
    }

    @Test
    fun testDefaultModelIsGemini38Flash() {
        assertEquals("gemini-3.8-flash-high", AntigravityModelCatalog.defaultModel.id)
        assertEquals(AntigravityModelCatalog.defaultModel, AntigravityModelCatalog.selectedModel.value)
    }

    @Test
    fun testSelectModelUpdatesStateFlow() {
        val sonnet = AntigravityModelCatalog.models.find { it.id == "claude-sonnet-4-6" }!!
        AntigravityModelCatalog.selectModel(sonnet)
        assertEquals("claude-sonnet-4-6", AntigravityModelCatalog.selectedModel.value.id)
        assertTrue(AntigravityModelCatalog.selectedModel.value.isThinking)
    }

    @Test
    fun testSelectModelById() {
        val success = AntigravityModelCatalog.selectModelById("claude-opus-4-6-thinking")
        assertTrue(success)
        assertEquals("claude-opus-4-6-thinking", AntigravityModelCatalog.selectedModel.value.id)

        val byDisplayName = AntigravityModelCatalog.selectModelById("3.7 Flash")
        assertTrue(byDisplayName)
        assertEquals("gemini-3.7-flash-high", AntigravityModelCatalog.selectedModel.value.id)

        val failed = AntigravityModelCatalog.selectModelById("non-existent-model")
        assertFalse(failed)
        assertEquals("gemini-3.7-flash-high", AntigravityModelCatalog.selectedModel.value.id)
    }

    @Test
    fun testSelectModelByIdFlexibleSearch() {
        // gemini-3.8-flash and 3.8 should match gemini-3.8-flash-high
        assertTrue(AntigravityModelCatalog.selectModelById("gemini-3.8-flash"))
        assertEquals("gemini-3.8-flash-high", AntigravityModelCatalog.selectedModel.value.id)

        assertTrue(AntigravityModelCatalog.selectModelById("3.8"))
        assertEquals("gemini-3.8-flash-high", AntigravityModelCatalog.selectedModel.value.id)

        // claude-sonnet and claude should match claude-sonnet-4-6
        assertTrue(AntigravityModelCatalog.selectModelById("claude-sonnet"))
        assertEquals("claude-sonnet-4-6", AntigravityModelCatalog.selectedModel.value.id)

        // Reset and test "claude"
        AntigravityModelCatalog.selectModel(AntigravityModelCatalog.defaultModel)
        assertTrue(AntigravityModelCatalog.selectModelById("claude"))
        assertEquals("claude-sonnet-4-6", AntigravityModelCatalog.selectedModel.value.id)

        // Test with dots as well: claude-sonnet-4.6, claude-opus-4.6
        assertTrue(AntigravityModelCatalog.selectModelById("claude-sonnet-4.6"))
        assertEquals("claude-sonnet-4-6", AntigravityModelCatalog.selectedModel.value.id)

        assertTrue(AntigravityModelCatalog.selectModelById("claude-opus-4.6"))
        assertEquals("claude-opus-4-6-thinking", AntigravityModelCatalog.selectedModel.value.id)

        // Test gpt-oss
        assertTrue(AntigravityModelCatalog.selectModelById("gpt-oss"))
        assertEquals("gpt-oss-120b-medium", AntigravityModelCatalog.selectedModel.value.id)
    }
}
