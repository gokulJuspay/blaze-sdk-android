package `in`.breeze.blaze

import org.json.JSONObject

internal data class SafeMargin(
  val top: Int = 0,
  val right: Int = 0,
  val bottom: Int = 0,
  val left: Int = 0
) {
  fun toJson(): JSONObject {
    return JSONObject()
      .put("top", top)
      .put("right", right)
      .put("bottom", bottom)
      .put("left", left)
  }
}

internal data class SdkParams(val safeMargin: SafeMargin) {
  fun toJson(): JSONObject {
    return JSONObject().put("safeMargin", safeMargin.toJson())
  }
}
