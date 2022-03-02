package org.devtcg.robotrc.robotview.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import org.devtcg.robotrc.R
import kotlin.math.max
import kotlin.math.min

class VerticalSlider @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
  companion object {
    private const val TAG = "VerticalSlider"
  }

  private val sliderBarWidth = 2 * resources.displayMetrics.density
  private val thumbWidth = 20 * resources.displayMetrics.density

  private val sliderPaint = Paint().apply {
    color = Color.LTGRAY
    flags = Paint.ANTI_ALIAS_FLAG
    strokeWidth = sliderBarWidth
  }

  private val thumbPaint = Paint().apply {
    color = MaterialColors.getColor(this@VerticalSlider, R.attr.colorPrimary)
    flags = Paint.ANTI_ALIAS_FLAG
  }

  init {
    minimumWidth = (50 * resources.displayMetrics.density).toInt()
    minimumHeight = (100 * resources.displayMetrics.density).toInt()
  }

  private val listeners = mutableListOf<OnChangeListener>()

  /**
   * If false, the position will be forcefully set back to [valueNeutral] when the user's finger
   * is released; otherwise the user's touch up event does nothing special.
   */
  var sticky: Boolean = true
  set(newSticky) {
    field = newSticky
    if (newSticky) {
      value = valueNeutral
    }
  }

  var touchEnabled: Boolean = true
  set(newTouchEnabled) {
    field = newTouchEnabled
    if (!newTouchEnabled) {
      isTouchDown = false
    }
    invalidate()
  }

  private var isTouchDown = false

  fun setValueRange(valueFrom: Float, valueTo: Float, valueNeutral: Float) {
    check(valueFrom <= valueTo)
    check(valueTo >= valueFrom)
    check(valueNeutral >= valueFrom)
    check(valueNeutral <= valueTo)

    this.valueFrom = valueFrom
    this.valueTo = valueTo
    this.valueNeutral = valueNeutral

    if (sticky) {
      value = valueNeutral
    }
    invalidate()
  }

  var valueFrom: Float = 0F
  private set
  var valueNeutral: Float = 0F
  private set
  var valueTo: Float = 100F
  private set

  private var _value: Float = 0F
  var value
  get() = _value
  set(providedValue) {
    setValueInternal(providedValue, fromUser = false)
  }

  fun addChangeListener(listener: OnChangeListener) {
    listeners.add(listener)
  }

  fun removeChangeListener(listener: OnChangeListener) {
    listeners.remove(listener)
  }

  override fun onDraw(canvas: Canvas) {
    val halfWidth = width / 2F
    val halfThumbWidth = thumbWidth / 2F
    val topBottomPadding = paddingTop + paddingBottom

    canvas.drawLine(
      halfWidth,
      paddingTop.toFloat(),
      halfWidth,
      height.toFloat() - paddingBottom,
      sliderPaint)

    val valueAsProportion = (value - valueFrom) / (valueTo - valueFrom)
    val heightLessPadding = height - topBottomPadding
    canvas.drawCircle(
      halfWidth,
      height - paddingBottom - (valueAsProportion * heightLessPadding),
      halfThumbWidth,
      thumbPaint)
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (!touchEnabled) {
      return false
    }
    return when (event.action) {
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        isTouchDown = false
        if (!sticky) {
          setValueInternal(valueNeutral, fromUser = true)
        }
        true
      }
      MotionEvent.ACTION_MOVE, MotionEvent.ACTION_DOWN -> {
        isTouchDown = true
        val usableHeight = (height - (paddingTop + paddingBottom)).toFloat()
        val clampedY = clamp(event.y, paddingTop.toFloat(), (height - paddingBottom).toFloat())
        val invertedProportion = 1F - ((clampedY - paddingTop) / usableHeight)
        val newValue = valueFrom + (invertedProportion * (valueTo - valueFrom))
        setValueInternal(newValue, fromUser = true)
        true
      }
      else -> false
    }
  }

  private fun setValueInternal(providedValue: Float, fromUser: Boolean) {
    if (!fromUser && isTouchDown) {
      Log.w(TAG, "Ignoring programmatic value while user touch is down: value=$providedValue")
      return
    }
    val clampedValue = clampValue(providedValue)
    if (clampedValue != providedValue) {
      Log.d(TAG, "Clamping $providedValue to $clampedValue...")
    }
    val oldValue = _value
    _value = clampedValue
    if (clampedValue != oldValue) {
      for (listener in listeners) {
        listener.onSliderValueChanged(this, clampedValue, fromUser)
      }
      invalidate()
    }
  }

  fun clampValue(providedValue: Float) = clamp(providedValue, valueFrom, valueTo)

  private fun clamp(value: Float, valueMin: Float, valueMax: Float) =
    min(max(value, valueMin), valueMax)

  fun interface OnChangeListener {
    fun onSliderValueChanged(view: VerticalSlider, value: Float, fromUser: Boolean)
  }
}