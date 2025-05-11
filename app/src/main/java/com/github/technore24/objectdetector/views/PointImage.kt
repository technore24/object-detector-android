package com.github.technore24.objectdetector.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View.OnClickListener
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.github.technore24.objectdetector.R
import kotlin.math.min

class PointImage : FrameLayout {
    private val context: Context
    private var image: ImageView? = null
    private var focus: ImageView? = null
    private val pointDataMap: MutableMap<ImageView, PointData> = HashMap()
    private var activePointId = -1
    private var pointClickListener: PointClickListener? = null
    private var objectAnimator: ObjectAnimator? = null
    private var imageBitmap: Bitmap? = null

    constructor(context: Context) : super(context) {
        this.context = context
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        this.context = context
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        this.context = context
        init()
    }

    private fun init() {
        image = ImageView(context)
        val mainParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        image!!.layoutParams = mainParams
        addView(image)

        focus = ImageView(context)
        focus!!.visibility = GONE
        addView(focus)
    }

    fun setImage(bitmap: Bitmap?) {
        imageBitmap = bitmap
        image!!.setImageBitmap(bitmap)
    }

    fun reset() {
        imageBitmap = null
        pointDataMap.clear()
        cancelAllAnimations()
        removeAllViews()
        init()
    }

    fun addPoint(id: Int, title: String?, rectF: RectF) {
        val centerX = rectF.centerX().toInt()
        val centerY = rectF.centerY().toInt()

        val pointImg = ImageView(context)
        pointImg.setImageResource(R.drawable.point)
        pointImg.setPadding(50, 50, 50, 50)

        val pointImgParams = LayoutParams(146, 146)
        pointImgParams.leftMargin = centerX - (pointImgParams.width / 2)
        pointImgParams.topMargin = centerY - (pointImgParams.height / 2)
        pointImg.layoutParams = pointImgParams
        addView(pointImg)

        val pointAnimImg = ImageView(context)
        pointAnimImg.setImageResource(R.drawable.point_stroke)

        val pointAnimParams = LayoutParams(40, 40)
        pointAnimParams.leftMargin = centerX - (pointAnimParams.width / 2)
        pointAnimParams.topMargin = centerY - (pointAnimParams.height / 2)
        pointAnimImg.layoutParams = pointAnimParams
        addView(pointAnimImg)

        val pointData = PointData(id, title)
        pointDataMap[pointAnimImg] = pointData

        pointImg.setOnClickListener(OnClickListener {
            val data = pointDataMap[pointAnimImg]
            if (activePointId == data!!.id) {
                return@OnClickListener
            }
            cancelAllAnimations()
            animatePoint(pointAnimImg)
            activePointId = data.id
            if (pointClickListener != null) {
                pointClickListener!!.onPointClick(data.id, data.title)
            }
            focus(rectF)
        })

        if (pointDataMap.size == 1) {
            activePointId = pointData.id
            animatePoint(pointAnimImg)
            focus(rectF)
        }
    }

    private fun animatePoint(point: ImageView) {
        objectAnimator = ObjectAnimator.ofPropertyValuesHolder(
            point,
            PropertyValuesHolder.ofFloat("scaleX", 2.0f),
            PropertyValuesHolder.ofFloat("scaleY", 2.0f)
        )
        objectAnimator!!.setDuration(1800)
        objectAnimator!!.interpolator = FastOutSlowInInterpolator()
        objectAnimator!!.repeatCount = ObjectAnimator.INFINITE
        objectAnimator!!.repeatMode = ObjectAnimator.REVERSE
        objectAnimator!!.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                animation.removeListener(this)
                animation.setDuration(0)
                animation.interpolator =
                    Interpolator { input -> 1 - LinearInterpolator().getInterpolation(input) }
                animation.start()
            }
        })
        objectAnimator!!.start()
    }

    private fun cancelAllAnimations() {
        if (objectAnimator != null) {
            objectAnimator!!.cancel()
        }
    }

    private fun focus(rectF: RectF) {
        focus!!.setImageBitmap(getFocusIcon(imageBitmap!!, rectF))
        focus!!.scaleX = 0.6f
        focus!!.scaleY = 0.6f
        focus!!.visibility = VISIBLE
        focus!!.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1f)
            .setInterpolator(DecelerateInterpolator())
            .setDuration(300)
            .start()
        Handler(Looper.getMainLooper()).postDelayed({
            focus!!.animate()
                .scaleX(0.8f)
                .scaleY(0.8f)
                .alpha(0f)
                .setInterpolator(DecelerateInterpolator())
                .setDuration(300)
                .withEndAction { focus!!.visibility = GONE }
                .start()
        }, 2000)
    }

    private fun getFocusIcon(baseBitmap: Bitmap, rectF: RectF): Bitmap {
        val bitmap = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)

        val paint = Paint()
        paint.color = Color.WHITE
        paint.strokeWidth = 5f
        paint.style = Paint.Style.STROKE
        paint.isAntiAlias = true

        val path = Path()

        val length = (min(
            rectF.width().toDouble(),
            rectF.height().toDouble()
        ) / 4).toFloat()
        val radius = 30f

        path.reset()

        // Top-left corner
        path.moveTo(rectF.left, rectF.top + length)
        path.lineTo(rectF.left, rectF.top + radius)
        path.arcTo(
            RectF(rectF.left, rectF.top, rectF.left + radius * 2, rectF.top + radius * 2),
            180f,
            90f
        )
        path.lineTo(rectF.left + length, rectF.top)

        // Top-right corner
        path.moveTo(rectF.right - length, rectF.top)
        path.lineTo(rectF.right - radius, rectF.top)
        path.arcTo(
            RectF(rectF.right - radius * 2, rectF.top, rectF.right, rectF.top + radius * 2),
            270f,
            90f
        )
        path.lineTo(rectF.right, rectF.top + length)

        // Bottom-right corner
        path.moveTo(rectF.right, rectF.bottom - length)
        path.lineTo(rectF.right, rectF.bottom - radius)
        path.arcTo(
            RectF(
                rectF.right - radius * 2,
                rectF.bottom - radius * 2,
                rectF.right,
                rectF.bottom
            ), 0f, 90f
        )
        path.lineTo(rectF.right - length, rectF.bottom)

        // Bottom-left corner
        path.moveTo(rectF.left + length, rectF.bottom)
        path.lineTo(rectF.left + radius, rectF.bottom)
        path.arcTo(
            RectF(
                rectF.left,
                rectF.bottom - radius * 2,
                rectF.left + radius * 2,
                rectF.bottom
            ), 90f, 90f
        )
        path.lineTo(rectF.left, rectF.bottom - length)

        canvas.drawPath(path, paint)

        return bitmap
    }

    interface PointClickListener {
        fun onPointClick(id: Int, title: String?)
    }

    fun setPointClickListener(listener: PointClickListener?) {
        pointClickListener = listener
    }

    private class PointData(var id: Int, var title: String?)
}

