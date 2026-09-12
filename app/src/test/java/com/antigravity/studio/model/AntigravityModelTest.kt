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
        assertEquals("gemini-3.8-flash", models[0].id)
        assertEquals("Gemini 3.8 Flash", models[0].displayName)
        assertEquals(listOf("High", "Fast"), models[0].tags)
        assertFalse(models[0].isThinking)

        assertEquals("gemini-3.7-flash", models[1].id)
        assertEquals("gemini-3.6-flash", models[2].id)
        assertEquals("gemini-3.1-pro", models[3].id)

        assertEquals("claude-sonnet-4.6", models[4].id)
        assertTrue(models[4].isThinking)

        assertEquals("claude-opus-4.6", models[5].id)
        assertTrue(models[5].isThinking)

        assertEquals("gpt-oss-120b", models[6].id)
    }

    @Test
    fun testDefaultModelIsGemini38Flash() {
        assertEquals("gemini-3.8-flash", AntigravityModelCatalog.defaultModel.id)
        assertEquals(AntigravityModelCatalog.defaultModel, AntigravityModelCatalog.selectedModel.value)
    }

    @Test
    fun testSelectModelUpdatesStateFlow() {
        val sonnet = AntigravityModelCatalog.models.find { it.id == "claude-sonnet-4.6" }!!
        AntigravityModelCatalog.selectModel(sonnet)
        assertEquals("claude-sonnet-4.6", AntigravityModelCatalog.selectedModel.value.id)
        assertTrue(AntigravityModelCatalog.selectedModel.value.isThinking)
    }

    @Test
    fun testSelectModelById() {
        val success = AntigravityModelCatalog.selectModelById("claude-opus-4.6")
        assertTrue(success)
        assertEquals("claude-opus-4.6", AntigravityModelCatalog.selectedModel.value.id)

        val byDisplayName = AntigravityModelCatalog.selectModelById("3.7 Flash")
        assertTrue(byDisplayName)
        assertEquals("gemini-3.7-flash", AntigravityModelCatalog.selectedModel.value.id)

        val failed = AntigravityModelCatalog.selectModelById("non-existent-model")
        assertFalse(failed)
        assertEquals("gemini-3.7-flash", AntigravityModelCatalog.selectedModel.value.id)
    }
}
