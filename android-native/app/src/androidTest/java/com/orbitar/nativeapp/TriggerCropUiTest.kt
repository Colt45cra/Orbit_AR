package com.orbitar.nativeapp

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TriggerCropUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun applyingCropUsesPreviewBoundsAndReportsPhysicalWidthRatio() {
        val source = Bitmap.createBitmap(1000, 1200, Bitmap.Config.ARGB_8888)
        source.eraseColor(android.graphics.Color.YELLOW)
        source.setPixel(100, 300, android.graphics.Color.RED)
        var output: Bitmap? = null
        var ratio = 0f
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                MaterialTheme {
                    TriggerCropDialog(source, onDismiss = {}) { bitmap, widthRatio -> output = bitmap; ratio = widthRatio }
                }
            }
        }
        rule.onNodeWithTag("crop-top").performSemanticsAction(SemanticsActions.SetProgress) { it(.25f) }
        rule.onNodeWithTag("crop-left").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.1f) }
        rule.onNodeWithTag("apply-trigger-crop").performClick()
        rule.runOnIdle {
            assertEquals(900, output!!.width)
            assertEquals(900, output!!.height)
            assertEquals(.9f, ratio, .0001f)
            assertEquals(android.graphics.Color.RED, output!!.getPixel(0, 0))
        }
    }
}
