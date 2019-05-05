@file:Suppress("unused", "NOTHING_TO_INLINE")

package com.squareup.contour

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import com.squareup.contour.constraints.SizeConfig
import com.squareup.contour.resolvers.ScalarResolver
import com.squareup.contour.utils.toXInt
import com.squareup.contour.utils.toYInt
import com.squareup.contour.utils.unwrapXIntToXInt
import com.squareup.contour.utils.unwrapYIntToYInt
import com.squareup.contour.wrappers.HasDimensions
import com.squareup.contour.wrappers.ParentGeometryProvider
import com.squareup.contour.wrappers.ViewDimensions

private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

open class ContourLayout(
  context: Context,
  attrs: AttributeSet? = null
) : ViewGroup(context, attrs), ContourScope {

  private val density = context.resources.displayMetrics.density

  val Int.dip: Int
    get() = (density * this).toInt()

  val Float.dip: Float
    get() = density * this

  inline fun Int.toXInt(): XInt = XInt(this)
  inline fun Float.toXInt(): XInt = XInt(toInt())
  inline fun Int.toYInt(): YInt = YInt(this)
  inline fun Float.toYInt(): YInt = YInt(toInt())

  private val widthConfig = SizeConfig()
  private val heightConfig = SizeConfig()
  private val geometryProvider = ParentGeometryProvider(widthConfig, heightConfig)
  private var initialized: Boolean = true

  fun <T : View> T.contourOf(
    addToViewGroup: Boolean = true,
    config: T.() -> LayoutSpec
  ): T {
    val viewGroup = this@ContourLayout
    val spec = config()
    spec.dimen = ViewDimensions(this)
    spec.parent = viewGroup.geometryProvider
    layoutParams = spec
    if (addToViewGroup) {
      viewGroup.addView(this)
    }
    return this
  }

  fun widthOf(config: (available: XInt) -> XInt) {
    widthConfig.lambda = unwrapXIntToXInt(config)
  }

  fun heightOf(config: (available: YInt) -> YInt) {
    heightConfig.lambda = unwrapYIntToYInt(config)
  }

  override fun requestLayout() {
    if (initialized) {
      widthConfig.clear()
      heightConfig.clear()
      for (i in 0 until childCount) {
        val child = getChildAt(i)
        (child.layoutParams as? LayoutSpec)?.clear()
      }
    }
    super.requestLayout()
  }

  override fun onMeasure(
    widthMeasureSpec: Int,
    heightMeasureSpec: Int
  ) {
    widthConfig.available = MeasureSpec.getSize(widthMeasureSpec)
    heightConfig.available = MeasureSpec.getSize(heightMeasureSpec)
    setMeasuredDimension(widthConfig.resolve(), heightConfig.resolve())
  }

  override fun onLayout(
    changed: Boolean,
    l: Int,
    t: Int,
    r: Int,
    b: Int
  ) {
    for (i in 0 until childCount) {
      val child = getChildAt(i)
      val params = child.layoutParams as LayoutSpec
      child.measure(params.x.measureSpec(), params.y.measureSpec())
      child.layout(
          params.left().value, params.top().value,
          params.right().value, params.bottom().value
      )
    }
  }

  class LayoutSpec(
    x: XResolver,
    y: YResolver
  ) : ViewGroup.LayoutParams(WRAP, WRAP), LayoutContext {

    internal val x = x as ScalarResolver
    internal val y = y as ScalarResolver

    override lateinit var parent: GeometryProvider
    internal lateinit var dimen: HasDimensions

    init {
      this.x.onAttach(this)
      this.y.onAttach(this)
    }

    internal fun left(): XInt = x.min().toXInt()
    internal fun right(): XInt = x.max().toXInt()
    internal fun centerX(): XInt = x.mid().toXInt()
    internal fun top(): YInt = y.min().toYInt()
    internal fun bottom(): YInt = y.max().toYInt()
    internal fun centerY(): YInt = y.mid().toYInt()
    internal fun width(): XInt = x.range().toXInt()
    internal fun height(): YInt = y.range().toYInt()

    internal fun preferredHeight(): YInt {
      dimen.measure(x.measureSpec(), 0)
      return dimen.height.toYInt()
    }

    internal fun measureSelf() {
      dimen.measure(x.measureSpec(), y.measureSpec())
      x.onRangeResolved(dimen.width)
      y.onRangeResolved(dimen.height)
    }

    internal fun clear() {
      x.clear()
      y.clear()
    }
  }
}
