package `in`.breeze.shopexample

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.breeze.blaze.Blaze
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID.randomUUID

private const val STORAGE_KEY = "blaze-shop-example"
private const val INITIATE_KEY = "initiate"
private const val PROCESS_KEY = "process"

private val colorScheme = lightColorScheme(primary = Color(0xFF8B5CF6))
private val codeStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)

class MainActivity : ComponentActivity() {

  private var blaze: Blaze? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WebView.setWebContentsDebuggingEnabled(true)

    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() {
        if (blaze?.handleBackPress() != false) {
          isEnabled = false
          onBackPressedDispatcher.onBackPressed()
        }
      }
    })

    val prefs = getSharedPreferences(STORAGE_KEY, MODE_PRIVATE)
    val activityContext = this
    setContent {

      val initiatePayload = remember {
        mutableStateOf(prefs.getString(INITIATE_KEY, null) ?: defaultInitiatePayload())
      }
      val processPayload = remember {
        mutableStateOf(prefs.getString(PROCESS_KEY, null) ?: defaultProcessPayload())
      }
      val error = remember { mutableStateOf<String?>(null) }
      val messages = remember { mutableStateOf("") }

      fun log(direction: String, eventName: String, eventData: JSONObject) {
        messages.value = "${now()} $direction $eventName\n${eventData.toString(2)}\n\n" + messages.value
      }

      fun readPayload(label: String, text: String): JSONObject? {
        return try {
          error.value = null
          JSONObject(text)
        } catch (e: JSONException) {
          error.value = "$label: ${e.message?.substringBefore(" of ")}"
          null
        }
      }

      MaterialTheme(colorScheme = colorScheme) {
        Surface {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .imePadding()
              .verticalScroll(rememberScrollState())
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Text("Breeze Shop Example", style = MaterialTheme.typography.titleLarge)

            PayloadEditor("Initiate payload", initiatePayload.value) {
              initiatePayload.value = it
              prefs.edit().putString(INITIATE_KEY, it).apply()
            }
            PayloadEditor("Process payload", processPayload.value) {
              processPayload.value = it
              prefs.edit().putString(PROCESS_KEY, it).apply()
            }

            error.value?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Button(onClick = {
                readPayload("Initiate payload", initiatePayload.value)?.let { payload ->
                  // One Blaze instance initiates only once, so each Initiate uses a new one.
                  blaze?.terminate()
                  log("→", "initiate", payload)
                  blaze = Blaze()
                  blaze?.initiate(activityContext, payload) { callbackEvent ->
                    // Blaze calls back on the WebView JavaBridge thread, not the main thread.
                    runOnUiThread {
                      val eventName = callbackEvent.optJSONObject("payload")?.optString("eventName")
                      log("←", eventName ?: "callback", callbackEvent)
                    }
                  }
                }
              }) {
                Text("Initiate SDK")
              }
              Button(onClick = {
                readPayload("Process payload", processPayload.value)?.let { payload ->
                  log("→", "process", payload)
                  blaze?.process(payload)
                }
              }) {
                Text("Process SDK")
              }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(onClick = {
                log("→", "terminate", JSONObject())
                blaze?.terminate()
                blaze = null
              }) {
                Text("Terminate SDK")
              }
              OutlinedButton(onClick = {
                prefs.edit().clear().apply()
                initiatePayload.value = defaultInitiatePayload()
                processPayload.value = defaultProcessPayload()
                error.value = null
              }) {
                Text("Reset")
              }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("Messages", style = MaterialTheme.typography.titleMedium)
              Spacer(Modifier.weight(1f))
              TextButton(onClick = { messages.value = "" }) {
                Text("Clear")
              }
            }
            Text(messages.value, style = codeStyle)
          }
        }
      }
    }
  }
}

@Composable
fun PayloadEditor(label: String, value: String, onValueChange: (String) -> Unit) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label) },
    textStyle = codeStyle,
    modifier = Modifier.fillMaxWidth()
  )
}

fun createSDKPayload(payload: JSONObject): JSONObject {
  val sdkPayload = JSONObject()
  sdkPayload.put("requestId", randomUUID().toString())
  sdkPayload.put("service", "in.breeze.shop")
  sdkPayload.put("payload", payload)
  return sdkPayload
}

fun createInitiatePayload(): JSONObject {
  val payload = JSONObject()
  payload.put("action", "initiate")
  payload.put("publisherId", "demo")
  payload.put("environment", "beta")
  return payload
}

fun createProcessPayload(): JSONObject {
  val payload = JSONObject()
  payload.put("action", "launch")
  return payload
}

fun defaultInitiatePayload(): String = createSDKPayload(createInitiatePayload()).toString(2)

fun defaultProcessPayload(): String = createSDKPayload(createProcessPayload()).toString(2)

fun now(): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
