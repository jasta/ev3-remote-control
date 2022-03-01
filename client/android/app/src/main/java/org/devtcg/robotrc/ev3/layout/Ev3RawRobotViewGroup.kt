package org.devtcg.robotrc.ev3.layout

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import org.devtcg.robotrc.databinding.Ev3PortBinding
import org.devtcg.robotrc.robotdata.network.DeviceDataFetcher
import kotlin.math.ceil
import kotlin.math.max

/**
 * Custom view that draws all of its children in a uniform grid filling the available space.
 * Minimum sizes are also enforced allowing this view group to naturally be placed inside scrolling
 * views.
 *
 * Assumes all child views should be the same width and height.
 */
class Ev3RawRobotViewGroup @JvmOverloads constructor(
  context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
  private val devicePortsByAddress = HashMap<String, Ev3PortBinding>()

  override fun onFinishInflate() {
    super.onFinishInflate()
    addDevicePorts()
  }

  private fun addDevicePorts() {
    // Note these are not localized on purpose, these port labels refer to actual labels printed
    // on the product and translating them would be confusing as they would no longer match.
    addDevicePort("ev3-ports:outA", "A")
    addDevicePort("ev3-ports:in1", "1")
    addDevicePort("ev3-ports:outB", "B")
    addDevicePort("ev3-ports:in2", "2")
    addDevicePort("ev3-ports:outC", "C")
    addDevicePort("ev3-ports:in3", "3")
    addDevicePort("ev3-ports:outD", "D")
    addDevicePort("ev3-ports:in4", "4")
  }

  private fun addDevicePort(deviceAddress: String, portLabel: String) {
    val portView = Ev3PortBinding.inflate(LayoutInflater.from(context), this, true)

    if (childCount % 2 == 0) {
      // Flip the order of the children for the bottom row
      reverseChildrenOrder(portView.root)
    }

    portView.portLabel.text = portLabel
    devicePortsByAddress[deviceAddress] = portView
  }

  private fun reverseChildrenOrder(parent: ViewGroup) {
    parent.run {
      val newOrder = (0 until childCount).reversed().map { getChildAt(it) }
      removeAllViews()
      for (child in newOrder) {
        addView(child)
      }
    }
  }

  fun getKnownAddresses(): List<String> {
    return devicePortsByAddress.keys.toList()
  }

  fun getPortBinding(deviceAddress: String): Ev3PortBinding? {
    return devicePortsByAddress[deviceAddress]
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val widthSize = MeasureSpec.getSize(widthMeasureSpec)
    val heightSize = MeasureSpec.getSize(heightMeasureSpec)

    val numColumns = ceil(childCount.toFloat() / 2).toInt()
    val firstChild = getChildAt(0)
    val eachChildMinWidth = firstChild.minimumWidth
    val eachChildMinHeight = firstChild.minimumHeight
    val eachChildConstrainedWidth = widthSize / numColumns
    val eachChildConstrainedHeight = heightSize / 2
    val eachChildWidth = max(eachChildMinWidth, eachChildConstrainedWidth)
    val eachChildHeight = max(eachChildMinHeight, eachChildConstrainedHeight)

    repeat(childCount) {
      val child = getChildAt(it)
      child.measure(
        MeasureSpec.makeMeasureSpec(eachChildWidth, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(eachChildHeight, MeasureSpec.EXACTLY))
    }

    setMeasuredDimension(
      MeasureSpec.makeMeasureSpec(eachChildWidth * numColumns, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(eachChildHeight * 2, MeasureSpec.EXACTLY))
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    var currentLeft = 0

    for (i in 0 until childCount step 2) {
      val topChild = getChildAt(i)

      topChild.layout(currentLeft, 0, currentLeft + topChild.measuredWidth, topChild.measuredHeight)

      if (i + 1 < childCount) {
        val bottomChild = getChildAt(i + 1)
        bottomChild.layout(
          currentLeft,
          topChild.measuredHeight,
          currentLeft + bottomChild.measuredWidth,
          topChild.measuredHeight + bottomChild.measuredHeight)
      }

      currentLeft += topChild.width
    }
  }
}