# Blaze SDK Android

The Blaze SDK is an easy-to-use toolkit that allows you to effortlessly integrate and utilize [Breeze 1 Click Checkout](https://breeze.in) & its services into your android app.

## Android SDK Integration

Follow the below steps to integrate Blaze SDK into your android application:

These steps open one-click checkout (`in.breeze.onecco`). For the shop assistant (`in.breeze.shop`), see [Shop assistant](#shop-assistant).

### Step 1: Obtaining the Blaze SDK

1.1. Add the JitPack repository to your project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
    // JitPack hosts the Blaze SDK
    maven { url = uri("https://jitpack.io") }
  }
}
```

1.2. Add the SDK to your app module's `build.gradle.kts`, using the latest version from the [releases page](https://github.com/juspay/blaze-sdk-android/releases):

```kotlin
dependencies {
  implementation("com.github.juspay:blaze-sdk-android:<version>")
}
```

The SDK's manifest is merged into your app's manifest. It adds the `INTERNET` permission and a `<queries>` entry for `upi://pay`, which lets the SDK find UPI payment apps. You don't need to add either yourself.

### Step 2: Initialize the SDK

**2.1. Create an instance of the `Blaze` class in your activity.**

```kotlin
val blaze = Blaze()
```

**2.2. Initiate the Blaze instance. Preferably in the `onCreate` method of your activity.**

Call `initiate` once. Further calls are ignored until you call `terminate` (Step 5).

In order to call initiate you need to perform the following steps:

#### 2.2.1: Construct the Initiate Payload

Create a Json with correct parameters to initiate the SDK. This is the data that will be used to initialize the SDK.

```kotlin
// Create a JSONObject for the Initiate data
val initiatePayload = JSONObject()
initiatePayload.put("merchantId", "<MERCHANT_ID>")
initiatePayload.put("environment", "<ENVIRONMENT>")
initiatePayload.put("shopUrl", "<SHOP_URL>")


// Place Initiate Payload into SDK Payload
val initSDKPayload = JSONObject()
initSDKPayload.put("requestId", "<UNIQUE_RANDOM_ID>")
initSDKPayload.put("service", "in.breeze.onecco")
initSDKPayload.put("payload", initiatePayload)

```

Note: Obtain values for `merchantId`, `environment` and `shopUrl` from the Breeze team.

All three are required; if one is missing, the `initiate` answer has `error: true`. `environment` is `beta`, `release`, `smbBeta` or `smbRelease`, and decides which Breeze environment the SDK loads.

#### 2.2.2: Construct the Callback Method

During the user journey the SDK will call the callback method with the result of the SDK operation.
You need to implement this method in order to handle the result of the SDK operation.

The callback receives a JSON object with `requestId`, `service` and `payload`. `payload.eventName` names the event: the first is `initiate`, in answer to your `initiate` call, and the rest depend on the service and the journey.

If the SDK could not read your payload, or an action failed, `payload.error` is `true` and `payload.errorDetails` says why.

The SDK calls the callback on a background thread. Switch to the main thread before updating your UI, for example with `runOnUiThread`.

```kotlin

fun blazeCallbackHandler (event: JSONObject) {
  val payload = event.optJSONObject("payload") ?: return
  val eventName = payload.optString("eventName")

  when(eventName) {
    // Handle various events according your desired logic
  }
}

```

#### 2.2.3: Call the initiate method on Blaze Instance

Finally, call the initiate method on the Blaze instance with the payload and the callback method.
The first parameter is your activity.

```kotlin
blaze.initiate(this, initSDKPayload, ::blazeCallbackHandler)
```

By default the SDK renders full screen, in the activity's `android.R.id.content` view.
To render it inside your own view instead, pass a `FrameLayout` as the third parameter. The SDK covers the whole area of this `FrameLayout`.

```kotlin
blaze.initiate(this, initSDKPayload, checkoutContainer, ::blazeCallbackHandler)
```

***Combined Example:***

```kotlin
class MainActivity : ComponentActivity() {

    // Create Blaze Instance
    val blaze = Blaze()

    override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)

      // 2.2 Initiate

      // 2.2.1 Create a JSONObject for the Initiate data
      val initiatePayload = JSONObject()
      initiatePayload.put("merchantId", "<MERCHANT_ID>")
      initiatePayload.put("environment", "<ENVIRONMENT>")
      initiatePayload.put("shopUrl", "<SHOP_URL>")

      // Place Initiate Payload into SDK Payload
      val initSDKPayload = JSONObject()
      initSDKPayload.put("requestId", "<UNIQUE_RANDOM_ID>")
      initSDKPayload.put("service", "in.breeze.onecco")
      initSDKPayload.put("payload", initiatePayload)

      // 2.2.3 Initiate Blaze SDK
      blaze.initiate(this, initSDKPayload, ::blazeCallbackHandler)
    }
}

// 2.2.2: Creating a Callback handler
fun blazeCallbackHandler (event: JSONObject) {
  val payload = event.optJSONObject("payload") ?: return
  val eventName = payload.optString("eventName")

  when(eventName) {
    // Handle various events according your desired logic
  }
}

```

### Step 3: Start processing your requests

Once the SDK is initiated, you can start processing your requests using the initialized instance of the SDK.
The SDK will call the callback method with the result of the SDK operation.

#### 3.1: Construct the Process Payload

Create a Json payload with the required parameters to process the request.
The process payload differs based on the request.
`action` is one of `startCheckout`, `startPayment`, `sendOTP`, `verifyOTP`, `listOffers`, `updateCustomer`, `listEMI`, `logout`, `manageProfile`, `manageAddress` or `verifyLogin`. Each action takes its own fields; get them from the Breeze team.

```kotlin

// 3.1 Create SDK Process Payload
// Create a JSONObject for the Process data
val processPayload = JSONObject()
processPayload.put("action", "<ACTION>")
// and more parameters required as per the action

// Place Process Payload into SDK Payload
val processSDKPayload = JSONObject()
processSDKPayload.put("requestId", "<UNIQUE_RANDOM_ID>")
processSDKPayload.put("service", "in.breeze.onecco")
processSDKPayload.put("payload", processPayload)

```

#### 3.2: Call the process method on Blaze Instance

Call the process method on the Blaze instance with the process payload to start the user journey or a headless flow.

```kotlin
blaze.process(processSDKPayload)
```

### Step 4: Handling Back Press

For making the hardware back button work as expected, you need to call the `handleBackPress` method on the Blaze instance.
Register an `OnBackPressedCallback` in your activity's `onCreate` and call it from there.
This method returns `true` when the SDK did not use the back press, and your app should handle it.

```kotlin
onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
  override fun handleOnBackPressed() {
    if (blaze.handleBackPress()) {
      isEnabled = false
      onBackPressedDispatcher.onBackPressed()
      isEnabled = true
    }
  }
})
```

### Step 5: Terminate the SDK

When you are done with the SDK, call `terminate`. It closes the journey and releases the SDK's WebView. Call `initiate` again before you use the SDK next time.

```kotlin
blaze.terminate()
```

### Optional Steps

`Blaze` also has `onActivityResult` and `onRequestPermissionsResult` methods. They are kept for compatibility and do nothing, so you don't need to forward these lifecycle callbacks to the Blaze instance.

### Shop assistant

The shop assistant (`in.breeze.shop`) is Breeze's shopping chat: inside it the shopper searches for products, builds a cart and checks out. You open it with the same `Blaze` calls as one-click checkout (`initiate`, `process`, `handleBackPress`, `terminate`); only the payloads, and what comes back, differ.

#### Initiate the shop assistant

```kotlin
val initiatePayload = JSONObject()
initiatePayload.put("action", "initiate")
initiatePayload.put("publisherId", "<PUBLISHER_ID>")
initiatePayload.put("environment", "<ENVIRONMENT>")

val initSDKPayload = JSONObject()
initSDKPayload.put("requestId", "<UNIQUE_RANDOM_ID>")
initSDKPayload.put("service", "in.breeze.shop")
initSDKPayload.put("payload", initiatePayload)

blaze.initiate(this, initSDKPayload, ::blazeCallbackHandler)
```

| Key | Required | Value |
| --- | --- | --- |
| `publisherId` | Yes | Your publisher id, from the Breeze team |
| `environment` | No | `beta` or `release`; picks which version of the assistant loads. Defaults to `release` |

`merchantId` and `shopUrl` are not used. `initiate` shows nothing on screen: it loads the assistant in the background, so it opens quickly later. The callback then receives `eventName: "initiate"` with `status: "success"`, or `error: true` with the reason in `errorDetails`, for example `Missing publisherId in initiate payload`.

#### Open the assistant

Once the `initiate` answer has arrived, call `process` to open the assistant full screen:

```kotlin
val processPayload = JSONObject()
processPayload.put("action", "launch")

val processSDKPayload = JSONObject()
processSDKPayload.put("requestId", "<UNIQUE_RANDOM_ID>")
processSDKPayload.put("service", "in.breeze.shop")
processSDKPayload.put("payload", processPayload)

blaze.process(processSDKPayload)
```

The callback receives `eventName: "eventStream"` with `payload.event` set to `ProcessStarted`.

#### What your app receives from the shop assistant

| `payload.eventName` | When it is sent |
| --- | --- |
| `initiate` | In answer to `initiate` |
| `eventStream`, with `payload.event` `ProcessStarted` | When the assistant opens, in answer to `process` |
| `process`, with `error: true` | When the `process` payload could not be read |

The assistant does not yet send events for the shopper's steps, or a `processResult` when the journey ends. When the shopper closes it, with its close button or a back press on its first screen, the SDK hides it but your app gets no callback. Call `process` again to reopen it.

#### Back press and terminate

These work as in Steps 4 and 5. A back press takes the assistant back one screen, and closes it when there is nothing to go back to. `terminate` removes it; call `initiate` again before you open it next time.

## Platform notes

What the SDK does inside your app (the network requests it makes, permissions, the methods the page can call, the data it stores and its size) is described in [Platform notes](docs/platform-notes.md).
