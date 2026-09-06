package com.example.myapplicationtest.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.ContextCompat
import com.example.myapplicationtest.model.RoadProblemType
import com.example.myapplicationtest.model.Severity
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

/**
 * Modern map pin for a road problem, drawn at screen density:
 * severity-colored outer ring + tail, type-colored core with a white icon,
 * a drop shadow for visibility, and — when [affectedBearing] is set — a small
 * compass badge on the top-right showing which travel direction is affected.
 */
fun createCustomMarkerIcon(
    context: Context,
    type: RoadProblemType,
    severity: Severity = Severity.MEDIUM,
    affectedBearing: Float? = null,
): BitmapDescriptor {
    val s = context.resources.displayMetrics.density
    val width = (44 * s).toInt()
    val height = (52 * s).toInt()
    val cx = 22 * s
    val cy = 20 * s

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val severityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = severity.color.toInt()
        style = Paint.Style.FILL
        setShadowLayer(3 * s, 0f, 1.5f * s, 0x50000000)
    }

    val tail = android.graphics.Path().apply {
        moveTo(cx - 6 * s, cy + 12 * s)
        lineTo(cx + 6 * s, cy + 12 * s)
        lineTo(cx, height - 2f * s)
        close()
    }
    canvas.drawPath(tail, severityPaint)

    canvas.drawCircle(cx, cy, 17 * s, severityPaint)
    canvas.drawCircle(cx, cy, 13.5f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    })
    canvas.drawCircle(cx, cy, 12 * s, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = type.color
        style = Paint.Style.FILL
    })

    ContextCompat.getDrawable(context, type.iconRes)?.let { drawable ->
        val r = (8.5f * s).toInt()
        drawable.setBounds((cx - r).toInt(), (cy - r).toInt(), (cx + r).toInt(), (cy + r).toInt())
        drawable.setTint(0xFFFFFFFF.toInt())
        drawable.draw(canvas)
    }

    if (affectedBearing != null) {
        val bx = 34.5f * s
        val by = 9f * s
        val br = 8.5f * s
        val badgeColor = 0xFF01579B.toInt()

        canvas.drawCircle(bx, by, br, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
            setShadowLayer(2 * s, 0f, s, 0x40000000)
        })
        canvas.drawCircle(bx, by, br - 0.75f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = badgeColor
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * s
        })

        canvas.save()
        canvas.rotate(affectedBearing, bx, by)
        val arrow = android.graphics.Path().apply {
            moveTo(bx, by - 5.5f * s)
            lineTo(bx + 4.5f * s, by + 4.5f * s)
            lineTo(bx, by + 2f * s)
            lineTo(bx - 4.5f * s, by + 4.5f * s)
            close()
        }
        canvas.drawPath(arrow, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = badgeColor
            style = Paint.Style.FILL
        })
        canvas.restore()
    }

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/**
 * Pin shown at the problem location while the user picks the affected traffic
 * direction. Styled like the problem pins (tail, rings, shadow) but teal with
 * a white crosshair target and a soft halo, so it reads as "point from here".
 */
fun createDirectionOriginIcon(context: Context): BitmapDescriptor {
    val s = context.resources.displayMetrics.density
    val width = (56 * s).toInt()
    val height = (62 * s).toInt()
    val cx = 28 * s
    val cy = 27 * s
    val accent = 0xFF00897B.toInt()

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // soft halo hinting "tap anywhere around me"
    canvas.drawCircle(cx, cy, 26 * s, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        alpha = 0x2E
        style = Paint.Style.FILL
    })
    canvas.drawCircle(cx, cy, 26 * s, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        alpha = 0x55
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * s
    })

    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        style = Paint.Style.FILL
        setShadowLayer(3 * s, 0f, 1.5f * s, 0x50000000)
    }

    val tail = android.graphics.Path().apply {
        moveTo(cx - 6 * s, cy + 12 * s)
        lineTo(cx + 6 * s, cy + 12 * s)
        lineTo(cx, height - 2f * s)
        close()
    }
    canvas.drawPath(tail, accentPaint)

    canvas.drawCircle(cx, cy, 17 * s, accentPaint)
    canvas.drawCircle(cx, cy, 13.5f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    })
    canvas.drawCircle(cx, cy, 12 * s, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        style = Paint.Style.FILL
    })

    // white crosshair target
    val glyphStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2 * s
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawCircle(cx, cy, 6.5f * s, glyphStroke)
    canvas.drawCircle(cx, cy, 2.5f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
    })
    for (angle in listOf(0f, 90f, 180f, 270f)) {
        canvas.save()
        canvas.rotate(angle, cx, cy)
        canvas.drawLine(cx, cy - 11f * s, cx, cy - 8.5f * s, glyphStroke)
        canvas.restore()
    }

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

fun getApiKeyFromManifest(context: Context): String? {
    return try {
        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName, PackageManager.GET_META_DATA
        )
        appInfo.metaData?.getString("com.google.android.geo.API_KEY")
    } catch (_: Exception) {
        null
    }
}
