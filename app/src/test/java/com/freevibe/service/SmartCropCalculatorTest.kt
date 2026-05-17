package com.freevibe.service

import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [SmartCropCalculator]. Geometry only; ML Kit not exercised.
 * `android.graphics.RectF` is available in unit-test classpath via the
 * `androidx.test.ext` deps already wired into the test runner.
 */
class SmartCropCalculatorTest {

    @Test
    fun centerSubject_landscape_bitmap_portrait_viewport_zoomsToFillAndCenters() {
        // 1600x900 wallpaper, subject is a 200x200 square centred at (400, 450)
        val subject = RectF(300f, 350f, 500f, 550f)
        val t = SmartCropCalculator.computeTransform(
            bitmapWidth = 1600,
            bitmapHeight = 900,
            subject = subject,
            viewportWidth = 720,
            viewportHeight = 1280,
        )
        // minScale floor = max(720/1600, 1280/900) = 1280/900 ≈ 1.422
        // coverage scale = min(720*0.75/200, 1280*0.75/200) = 2.7 (smaller dim)
        // result scale = max(2.7, floor) = 2.7
        assertEquals(2.7f, t.scale, 0.01f)
        // offsetX = scale * (1600/2 - 400) = 2.7 * 400 = 1080
        // offsetY = scale * (900/2  - 450) = 2.7 *   0 =    0
        assertEquals(1080f, t.offsetX, 0.5f)
        assertEquals(0f, t.offsetY, 0.5f)
    }

    @Test
    fun subjectOffCenter_offsetMovesItToViewportCenter() {
        // Subject near top-left corner of a square wallpaper
        val subject = RectF(100f, 100f, 200f, 200f)
        val t = SmartCropCalculator.computeTransform(
            bitmapWidth = 1000,
            bitmapHeight = 1000,
            subject = subject,
            viewportWidth = 720,
            viewportHeight = 1280,
        )
        // centre offsets push subject (cx=150, cy=150) toward viewport centre
        // offsetX = scale * (500 - 150) = scale * 350
        // offsetY = scale * (500 - 150) = scale * 350
        assertEquals(t.scale * 350f, t.offsetX, 0.5f)
        assertEquals(t.scale * 350f, t.offsetY, 0.5f)
    }

    @Test
    fun tinySubject_doesNotZoomBeyondMaxScale() {
        // A 1-pixel subject would otherwise demand huge zoom
        val subject = RectF(500f, 500f, 501f, 501f)
        val t = SmartCropCalculator.computeTransform(
            bitmapWidth = 1000,
            bitmapHeight = 1000,
            subject = subject,
            viewportWidth = 1000,
            viewportHeight = 1000,
            maxScale = 4f,
        )
        assertEquals(4f, t.scale, 0.001f)
    }

    @Test
    fun largeSubjectFillingMostOfBitmap_floorsAtViewportFill() {
        // Subject is almost the entire bitmap → fitting it to 75 % coverage gives < 1
        // The floor must clamp scale up so there's no letterboxing.
        val subject = RectF(0f, 0f, 900f, 900f)
        val t = SmartCropCalculator.computeTransform(
            bitmapWidth = 900,
            bitmapHeight = 900,
            subject = subject,
            viewportWidth = 720,
            viewportHeight = 1280,
        )
        val minScale = maxOf(720f / 900f, 1280f / 900f)
        assertTrue("scale $t.scale should be >= minScale $minScale", t.scale >= minScale - 0.001f)
    }

    @Test
    fun afterTransform_subjectScreenCenterEqualsViewportCenter() {
        val subject = RectF(120f, 480f, 380f, 720f) // off-centre subject
        val bitmapW = 1200; val bitmapH = 1600
        val viewW = 1080; val viewH = 1920
        val t = SmartCropCalculator.computeTransform(
            bitmapWidth = bitmapW,
            bitmapHeight = bitmapH,
            subject = subject,
            viewportWidth = viewW,
            viewportHeight = viewH,
        )
        val cx = (subject.left + subject.right) / 2f
        val cy = (subject.top + subject.bottom) / 2f
        // Replicate cropBitmap mapping: subject_screen_x = imgLeft + cx*scale
        val imgLeft = (viewW - bitmapW * t.scale) / 2f + t.offsetX
        val imgTop = (viewH - bitmapH * t.scale) / 2f + t.offsetY
        val subjScreenX = imgLeft + cx * t.scale
        val subjScreenY = imgTop + cy * t.scale
        assertEquals(viewW / 2f, subjScreenX, 0.5f)
        assertEquals(viewH / 2f, subjScreenY, 0.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroBitmapDimensions() {
        SmartCropCalculator.computeTransform(
            bitmapWidth = 0,
            bitmapHeight = 100,
            subject = RectF(0f, 0f, 10f, 10f),
            viewportWidth = 100,
            viewportHeight = 100,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroViewportDimensions() {
        SmartCropCalculator.computeTransform(
            bitmapWidth = 100,
            bitmapHeight = 100,
            subject = RectF(0f, 0f, 10f, 10f),
            viewportWidth = 100,
            viewportHeight = 0,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCoverageOutOfRange() {
        SmartCropCalculator.computeTransform(
            bitmapWidth = 100,
            bitmapHeight = 100,
            subject = RectF(0f, 0f, 10f, 10f),
            viewportWidth = 100,
            viewportHeight = 100,
            targetCoverage = 0.05f,
        )
    }
}
