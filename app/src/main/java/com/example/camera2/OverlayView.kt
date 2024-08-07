package com.example.camera2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val paint = Paint()
    lateinit var rect: Rect

    init {
        paint.color = Color.RED
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Calcula las coordenadas del rectángulo centrado

        // Calcula las coordenadas del rectángulo horizontal centrado
        val rectWidth = (width).toInt()
        val rectHeight = (height * 0.25).toInt()
        val left = (width - rectWidth) / 2
        val top = (height - rectHeight) / 2
        val right = left + rectWidth
        val bottom = top + rectHeight

        rect = Rect(left, top, right, bottom)

        canvas.drawRect(rect, paint)
    }

}