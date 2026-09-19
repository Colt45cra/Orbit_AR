package com.orbitar.nativeapp
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import org.junit.Rule
import org.junit.Test
import java.io.File

class PlacementUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun imagesAndPlacementScrollAndPreviewShowsBothImages() {
        val trigger = Bitmap.createBitmap(300,200,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.YELLOW) }
        val popup = Bitmap.createBitmap(160,200,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.MAGENTA) }
        rule.activity.runOnUiThread { rule.activity.setContent { MaterialTheme { OrbitNativeApp(trigger,popup) } } }
        rule.onNodeWithTag("images-list").performScrollToNode(hasTestTag("images-end"))
        rule.onNodeWithTag("images-end").assertIsDisplayed()
        rule.onNodeWithTag("placement-tab").performClick()
        rule.onNodeWithTag("placement-preview").assertIsDisplayed()
        val image=rule.onNodeWithTag("placement-preview").captureToImage().asAndroidBitmap()
        var yellow=0; var magenta=0
        for(y in 0 until image.height) for(x in 0 until image.width) {
            val c=image.getPixel(x,y); val r=android.graphics.Color.red(c); val g=android.graphics.Color.green(c); val b=android.graphics.Color.blue(c)
            if(r>150 && g>150 && b<100) yellow++
            if(r>150 && b>150 && g<100) magenta++
        }
        org.junit.Assert.assertTrue("Trigger visible",yellow>50)
        org.junit.Assert.assertTrue("AR image visible",magenta>50)
        File(rule.activity.getExternalFilesDir(null),"placement.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        // A vertical swipe beginning on the preview must scroll, not move the image.
        val before = rule.onNodeWithTag("placement-preview").fetchSemanticsNode().boundsInRoot.top
        rule.onNodeWithTag("placement-preview").performTouchInput { swipeUp() }
        val after = rule.onNodeWithTag("placement-preview").fetchSemanticsNode().boundsInRoot.top
        org.junit.Assert.assertTrue("Swipe on preview scrolls the page", after < before)
        rule.onNodeWithTag("placement-list").performScrollToNode(hasTestTag("placement-end"))
        rule.onNodeWithTag("placement-end").assertIsDisplayed().performClick()
        rule.onNodeWithTag("images-tab").performClick()
        rule.onNodeWithTag("images-end").assertIsDisplayed()
    }
}
