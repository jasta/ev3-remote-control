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
  private val borderWidth = 1 * resources.displayMetrics.density

  private val trackPaddingLeftRight = 30 * resources.displayMetrics.density
  private val trackPaddingTopBottom = (thumbWidth / 2) + trackPaddingLeftRight
  private val trackVisualPaddingTopBottom = (thumbWidth / 2) + 6 * resources.displayMetrics.density
  private val trackWidth = thumbWidth + trackPaddingLeftRight

  init {
    minimumWidth = trackWidth.toInt()
    minimumHeight = (100 * resources.displayMetrics.density).toInt()
  }

  private val sliderPaint = Paint().apply {
    color = Color.LTGRAY
    flags = Paint.ANTI_ALIAS_FLAG
    strokeWidth = sliderBarWidth
  }

  private val trackBorderPaint = Paint().apply {
    color = MaterialColors.getColor(this@VerticalSlider, R.attr.colorOnPrimary)
    flags = Paint.ANTI_ALIAS_FLAG
    strokeWidth = borderWidth
    style = Paint.Style.STROKE
  }

  private val trackFillPaint = Paint().apply {
    color = MaterialColors.getColor(this@VerticalSlider, R.attr.colorSurface)
    flags = Paint.ANTI_ALIAS_FLAG
    style = Paint.Style.FILL
  }

  private val thumbPaint = Paint().apply {
    color = MaterialColors.getColor(this@VerticalSlider, R.attr.colorPrimary)
    flags = Paint.ANTI_ALIAS_FLAG
  }

  private val listeners = mutableListOf<OnChangeListener>()

  /**
   * If false, the position will be forcefully set back to [valueNeutral] when the user's finger
   * is released; otherwise the user's touch up event does nothing special.
   */
  var sticky: Boolean = true
  set(newSticky) {
    field = newSticky
    if (!newSticky) {
      // The logic is a little flimsy but technically we're setting to valueNeutral because the
      // _user_ doesn't have their finger down, simulating it as if the user's finger suddenly
      // lifted up when stick was turned off.
      setValueInternal(valueNeutral, fromUser = true)
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

    if (!sticky) {
      setValueInternal(valueNeutral, fromUser = false)
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

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    val halfLeftRight = (trackPaddingLeftRight / 2).toInt()
    val halfTopBottom = (trackPaddingTopBottom / 2).toInt()
    val clampedLeft = max(paddingLeft, halfLeftRight)
    val clampedRight = max(paddingRight, halfLeftRight)
    val clampedTop = max(paddingTop, halfTopBottom)
    val clampedBottom = max(paddingBottom, halfTopBottom)

    // Harmless call if our clamping didn't change the values.
    setPadding(clampedLeft, clampedTop, clampedRight, clampedBottom)
  }

  override fun onDraw(canvas: Canvas) {
    val halfWidth = width / 2F
    val halfTrackWidth = trackWidth / 2
    val halfThumbWidth = thumbWidth / 2F
    val halfTrackVisualPaddingTopBottom = trackVisualPaddingTopBottom / 2F
    val topBottomPadding = paddingTop + paddingBottom
    val trackPaddingTop = trackPaddingTopBottom / 2F
    val trackPaddingBottom = trackPaddingTop

    for (trackPaint in listOf(trackFillPaint, trackBorderPaint)) {
      canvas.drawRect(
        halfWidth - halfTrackWidth,
        halfTrackVisualPaddingTopBottom,
        halfWidth + halfTrackWidth,
        height - halfTrackVisualPaddingTopBottom,
        trackPaint)
    }

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
        if (event.action == MotionEvent.ACTION_DOWN) {
          parent.requestDisallowInterceptTouchEvent(true)
        }
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