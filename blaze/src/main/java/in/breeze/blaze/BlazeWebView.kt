package `in`.breeze.blaze

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.net.toUri
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference
import androidx.core.content.edit


internal class BlazeWebView @SuppressLint(
  "SetJavaScriptEnabled",
  "JavascriptInterface"
) constructor(
  context: Activity,
  private val initiatePayload: JSONObject,
  private val callbackFn: CallbackFn
) {

  private val contextRef: WeakReference<Activity> = WeakReference(context)
  private val webView: WebView = WebView(context)
  private var isWebViewReady: Boolean = false
  private var consumingBackPress: Boolean = false
  private var eventQueue: HashMap<String, JSONObject> = hashMapOf()
  private val sharedPreferences: SharedPreferences
  private var baseUrl: String = getBaseUrl(initiatePayload)
  private val frameLoadTarget: AtomicReference<BlazeWebView?> = AtomicReference(this)

  private val blazeWebViewClient = object : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url
        if (url != null && isUPIIntentUri(url)) {
            openApp(url.toString())
            return true
        }
        return false
    }

    @Deprecated("Required for API < 24 compatibility")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url != null && isUPIIntentUri(url.toUri())) {
            openApp(url)
            return true
        }
        return false
    }
  }

  init {
    this.sharedPreferences =
      context.getSharedPreferences(BlazeConstants.SHARED_PREF_NAME, Context.MODE_PRIVATE)
    this.webView.settings.javaScriptEnabled = true
    this.webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
    this.webView.settings.domStorageEnabled = true
    this.webView.setBackgroundColor(Color.TRANSPARENT)
    this.webView.webViewClient = blazeWebViewClient
    this.webView.addJavascriptInterface(this, "Native")
    this.sendEvent("initiate", this.initiatePayload)
    this.loadFrame(context)
  }

  private fun loadFrame(context: Activity) {
    val target = frameLoadTarget
    BlazeConfig.resolveFrameUrl(
      context,
      getService(initiatePayload),
      getEnvironment(initiatePayload)
    ) { frameUrl -> target.getAndSet(null)?.renderFrame(frameUrl) }
  }

  private fun renderFrame(frameUrl: String?) {
    if (frameUrl != null) {
      baseUrl = frameUrl
    }
    val activity = contextRef.get() ?: return
    if (activity.isFinishing || activity.isDestroyed) {
      return
    }
    activity.runOnUiThread { webView.loadUrl(baseUrl) }
  }


  fun process(payload: JSONObject) {
    this.sendEvent("process", payload)
  }

  fun handleBackPress(): Boolean {
    if (consumingBackPress) {
      contextRef.get()?.runOnUiThread {
        val currentPageUrl = webView.url
        val isBreezePage = currentPageUrl?.contains(baseUrl) ?: false
        if (isBreezePage) {
          this.sendEvent("backPress", JSONObject())
        } else if (webView.canGoBack()) {
          webView.goBack()
        }
      }
      return false
    }
    return true
  }

  fun terminate() {
    frameLoadTarget.set(null)
    this.sendEvent("terminate", JSONObject())
    hideView()
    contextRef.get()?.runOnUiThread {
      webView.stopLoading()
      webView.removeJavascriptInterface("Native")
      this.webView.destroy()
    }
    eventQueue.clear()
  }


  @JavascriptInterface
  fun onEvent(event: String) {
    val eventJson = safeParseJson(event)
    val eventName = eventJson.getString("eventName")
    val eventData = eventJson.optString("eventData")
    val eventDataJson = safeParseJson(eventData)

    if (eventName == "appReady") {
      isWebViewReady = true
      drainEventQueue()
    }
    if (eventName == "callbackEvent") {
      this.callbackFn.invoke(eventDataJson)
    }

    if (eventName == "consumeBackPress") {
      consumingBackPress = true
    }

    if (eventName == "releaseBackPress") {
      consumingBackPress = false
    }

    if (eventName == "renderView") {
      renderView()
    }

    if (eventName == "hideView") {
      hideView()
    }

    if (eventName == "invokeMethod") {
      handleInvokeMethod(eventDataJson)
    }

  }


  private fun sendEvent(event: String, payload: JSONObject) {
    val eventMessage =
      JSONObject()
        .put("eventName", event)
        .put("eventData", payload)
        .put("source", "blaze")
    if (isWebViewReady) {
      contextRef.get()?.runOnUiThread {
        webView.evaluateJavascript("javascript:onSDKEvent(JSON.stringify($eventMessage))") {}
      }
    } else {
      eventQueue[event] = payload
    }
  }

  private fun drainEventQueue() {
    val pendingEvents = eventQueue.toList()
    eventQueue.clear()
    pendingEvents.forEach { (event, payload) ->
      sendEvent(event, payload)
    }
  }

  private fun renderView() {
    contextRef.get()?.runOnUiThread {
      this.webView.layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      val rootView =
        contextRef.get()?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
      rootView?.addView(webView)
    }
  }

  private fun hideView() {
    contextRef.get()?.runOnUiThread {
      this.webView.layoutParams = FrameLayout.LayoutParams(0, 0)
      val rootView =
        contextRef.get()?.window?.decorView?.findViewById<ViewGroup>(android.R.id.content)
      rootView?.removeView(webView)
    }
  }

  private fun handleInvokeMethod(eventData: JSONObject) {
    val methodName = eventData.optString("methodName")
    val requestId = eventData.optString("requestId")
    val params = eventData.optString("params")
    val paramsJson = safeParseJson(params)

    val invokeResult = JSONObject().put("requestId", requestId).put("methodName", methodName)

    if (methodName == "saveToStorage") {
      val key = paramsJson.optString("key")
      val value = paramsJson.optString("value")
      invokeResult.put("methodResult", saveToStorage(key, value))
    } else if (methodName == "openApp") {
      val intentUri = paramsJson.optString("intentUri")
      invokeResult.put("methodResult", openApp(intentUri))
    } else if (methodName == "getFromStorage") {
      val key = paramsJson.optString("key")
      val value = getFromStorage(key)
      invokeResult.put("methodResult", JSONObject().put("key", key).put("value", value))
    } else if (methodName == "findApps") {
      val payload = paramsJson.optString("payload")
      invokeResult.put("methodResult", findApps(payload))
    } else {
      invokeResult.put("methodResult", "Method not found")
    }
    sendEvent("invokeMethodResult", invokeResult)
  }

  private fun saveToStorage(key: String, value: String): Boolean {
    try {
        sharedPreferences.edit() {
            putString(key, value)
        }
      return true
    } catch (e: Exception) {
      Log.e("BlazeSDK: Failure: ", e.message.toString())
      return false
    }
  }

  private fun getFromStorage(key: String): String? {
    try {
      return sharedPreferences.getString(key, null)
    } catch (e: Exception) {
      Log.e("BlazeSDK: Failure: ", e.message.toString())
      return null
    }
  }

  private fun openApp(
    intentUri: String
  ): String {
    try {
      val intent = Intent(Intent.ACTION_VIEW, intentUri.toUri())
      contextRef.get()?.startActivityForResult(intent, 0)
      return "Successfully triggered intent"
    } catch (e: Exception) {
      return "Failed to invoke intent: " + e.message.toString()
    }
  }

  private fun findApps(payload: String): String {
    val pm = contextRef.get()?.packageManager
    val apps = JSONArray()

    if (pm !== null) {
      val upiApps = Intent()
      upiApps.setData(payload.toUri())
      val launchables: List<ResolveInfo> = pm.queryIntentActivities(upiApps, 0)
      Collections.sort(launchables, ResolveInfo.DisplayNameComparator(pm))
      for (resolveInfo in launchables) {
        val jsonObject = JSONObject()
        try {
          val appInfo = pm.getApplicationInfo(resolveInfo.activityInfo.packageName, 0)
          jsonObject.put("packageName", appInfo.packageName)
          jsonObject.put("appName", pm.getApplicationLabel(appInfo))
          apps.put(jsonObject)
        } catch (e: Exception) {
          Log.e("BlazeSDK: findApps: ", e.message.toString())
        }
      }
    }
    return apps.toString()
  }

}