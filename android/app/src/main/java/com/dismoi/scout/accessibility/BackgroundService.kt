package com.dismoi.scout.accessibility

/*
  The configuration of an accessibility service is contained in the 
  AccessibilityServiceInfo class
*/
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.*
import android.provider.Settings.canDrawOverlays
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.dismoi.scout.accessibility.BackgroundModule.Companion.sendEventFromAccessibilityServicePermission
import com.dismoi.scout.accessibility.browser.Chrome
import com.facebook.react.HeadlessJsTaskService

class BackgroundService : AccessibilityService() {
  private var _hide: String? = ""
  private var _eventTime: String? = ""
  private var _packageName: String? = ""
  val chrome: Chrome = Chrome()

  private val NOTIFICATION_TIMEOUT: Long = 500

  private val TAG = "Accessibility"

  private val handler = Handler(Looper.getMainLooper())
  private val runnableCode: Runnable = object : Runnable {
    override fun run() {
      val context = applicationContext
      val myIntent = Intent(context, BackgroundEventService::class.java)
      val bundle = Bundle()

      bundle.putString("packageName", _packageName)
      bundle.putString("url", chrome._url)
      bundle.putString("hide", _hide)
      bundle.putString("eventTime", _eventTime)

      myIntent.putExtras(bundle)

      context.startService(myIntent)
      HeadlessJsTaskService.acquireWakeLockNow(context)
    }
  }

  private fun getEventType(event: AccessibilityEvent): String? {
    when (event.eventType) {
      AccessibilityEvent.TYPE_VIEW_CLICKED -> return "TYPE_VIEW_CLICKED"
      AccessibilityEvent.TYPE_VIEW_FOCUSED -> return "TYPE_VIEW_FOCUSED"
      AccessibilityEvent.TYPE_VIEW_SELECTED -> return "TYPE_VIEW_SELECTED"
      AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> return "TYPE_WINDOW_STATE_CHANGED"
      AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> return "TYPE_WINDOW_CONTENT_CHANGED"
      AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> return "TYPE_VIEW_TEXT_CHANGED"
      AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> return "TYPE_VIEW_TEXT_SELECTION_CHANGED"
      AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> return "TYPE_VIEW_ACCESSIBILITY_FOCUSED"
      AccessibilityEvent.TYPE_WINDOWS_CHANGED -> return "TYPE_WINDOWS_CHANGED"
    }
    return event.eventType.toString()
  }

  /* 
    This system calls this method when it successfully connects to your accessibility service
  */
  // configure my service in there
  @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  override fun onServiceConnected() {

    val info = serviceInfo
    
    // Set the type of events that this service wants to listen to. Others
    // won't be passed to this service
    /* 
      Represents the event of changing the content of a window and more specifically 
      the sub-tree rooted at the event's source
    */
    info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED

    //AccessibilityEvent.TYPE_WINDOWS_CHANGED or
    /*
      info.packageNames is not set because we want to receive event from
      all packages
     */

    info.feedbackType = AccessibilityServiceInfo.FEEDBACK_VISUAL
    info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

    /* 
      The minimal period in milliseconds between two accessibility events of 
      the same type are sent to this service
    */
    info.notificationTimeout = NOTIFICATION_TIMEOUT

    this.serviceInfo = info
  }

  private fun overlayIsActivated(applicationContext: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      canDrawOverlays(applicationContext)
    } else {
      false
    }
  }

  private fun isWindowChangeEvent(event: AccessibilityEvent): Boolean {
    return AccessibilityEvent.eventTypeToString(event.eventType).contains("WINDOW")
  }

  private fun chromeSearchBarEditingIsActivated(info: AccessibilityNodeInfo): Boolean {
    return info.childCount > 0 &&
      info.className.toString() == "android.widget.FrameLayout" &&
      info.getChild(0).className.toString() == "android.widget.EditText"
  }

  fun isLauncherActivated(packageName: String): Boolean {
    return "com.android.launcher3" == packageName
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun findByClassName(node: AccessibilityNodeInfo, className: String, level: Int = 0): AccessibilityNodeInfo? {
    node.refresh()
    val count = node.childCount
    for (i in 0 until count) {
      val child = node.getChild(i)
      if (child != null) {
        if (child.className.toString() == className) {
          return child
        }
        val foundInChild = findByClassName(child, className, level + 1)
        if (foundInChild != null) return foundInChild
      }
    }
    return null
  }

  @RequiresApi(Build.VERSION_CODES.P)
  private fun findHeading(node: AccessibilityNodeInfo, level: Int = 0): AccessibilityNodeInfo? {
    node.refresh()
    val count = node.childCount
    for (i in 0 until count) {
      val child = node.getChild(i)
      if (child != null) {
        if (child?.isHeading) {
          return child
        }
        val foundInChild = findHeading(child, level + 1)
        if (foundInChild != null) return foundInChild
      }
    }
    return null
  }


  @RequiresApi(Build.VERSION_CODES.O)
  private fun findById(node: AccessibilityNodeInfo, id: String, level: Int = 0): AccessibilityNodeInfo? {
    val count = node.childCount
    for (i in 0 until count) {
      val child = node.getChild(i)

      if (child != null) {
        if (child?.viewIdResourceName?.toString() == id) {
          return child
        }
        val foundInChild = findById(child, id, level + 1)
        if (foundInChild != null) return foundInChild
      }
    }
    return null
  }


  @RequiresApi(Build.VERSION_CODES.O)
  private fun findTexts(node: AccessibilityNodeInfo, level: Int = 0): String {
    val count = node.childCount
    var texts = ""
    for (i in 0 until count) {
      val child = node.getChild(i)

      texts += child.text?.toString() ?: child.contentDescription?.toString() ?: ""
      texts += "\n" + findTexts(child, level + 1) + "\n"
    }
    return texts
  }

  @RequiresApi(Build.VERSION_CODES.O)
  private fun findWebview(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
    return findByClassName(node, "android.webkit.WebView")
  }

  @RequiresApi(30)
  private fun logHierarchy(node: AccessibilityNodeInfo, level: Int = 0) {
    node.refresh()
    val id = node.viewIdResourceName
    val text = node.text?.toString()
    val content = node.contentDescription?.toString()
    val avExtras = node.availableExtraData?.joinToString(", ")
    val count = node.childCount

    val extras = node.extras
    val extrasList = mutableListOf<String>()
    for (key in extras.keySet()) {
      extrasList.add("$key: ${extras.get(key)}")
    }
    val allExtras = extrasList.joinToString(" / ")

    Log.d(TAG, "${"  ".repeat(level)} ($level) " +
      "className: ${node.className}, " +
      "id: ${id ?: "NO ID"}, " +
      "text: $text, " +
      "content: $content, " +
      "extras: $allExtras, " +
      "avExtras: $avExtras, " +
      "hint: ${node.hintText}, " +
      "heading: ${node.isHeading}, " +
      "inputType: ${node.inputType}, " +
      "state: ${node.stateDescription}"
    )

    for (i in 0 until count)  {
      val child = node.getChild(i)
      if (child != null) {
        logHierarchy(child, level + 1)
      }
    }
  }

  private fun getTextAndContent(node: AccessibilityNodeInfo?): String? {
    return if (node != null)  "${node.text?.toString()} / ${node.contentDescription?.toString()}" else null
  }

  /*
    This method is called back by the system when it detects an 
    AccessibilityEvent that matches the event filtering parameters 
    specified by your accessibility service
   */
  @RequiresApi(30)
  override fun onAccessibilityEvent(event: AccessibilityEvent) {
    Log.d(TAG, "Event : ${getEventType(event)}, Package: ${event.packageName}, Source: ${event.source?.className.toString()}")

    val root = rootInActiveWindow

    if (root == null) {
      return
    }

    if (root.packageName?.toString() == "com.android.chrome" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && root != null) {
      Log.d(TAG, "Active window packageName : ${root.packageName}, className: ${root.className}")
    }

    // Temporary demo code specific to Amazon
    val webview = findWebview(root)
    if (webview != null) {
      val titleExpanderContent = findById(webview, "titleExpanderContent")
      if (titleExpanderContent != null) {
        val titleView = findHeading(titleExpanderContent)
        val title = titleView?.text ?: titleView?.contentDescription
        Log.d(TAG, "Found Amazon page title : $title")
      }
    }

    if (overlayIsActivated(applicationContext)) {applicationContext
      val packageName = event.packageName?.toString() ?: "NO PACKAGE"

      if (getEventType(event) == "TYPE_WINDOW_STATE_CHANGED" && packageName != "com.android.chrome") {
        if (packageName.contains("com.google.android.inputmethod") ||
          packageName == "com.google.android.googlequicksearchbox" ||
          packageName == "com.android.systemui"
        ) {
          _packageName = packageName
          _hide = "true"
          handler.post(runnableCode)
          return
        }
      }

      if (isLauncherActivated(packageName)) {
        _hide = "true"
        _packageName = packageName
        handler.post(runnableCode)
        return
      }

      val parentNodeInfo: AccessibilityNodeInfo = event.source ?: return

      chrome.parentNodeInfo = parentNodeInfo
      chrome._packageName = packageName

      if (chrome.checkIfChrome()) {
        if (
          getEventType(event) == "TYPE_WINDOW_STATE_CHANGED" ||
          getEventType(event) == "TYPE_WINDOW_CONTENT_CHANGED"
        ) {
          chrome.captureUrl()
          if (chrome.chromeSearchBarEditingIsActivated()) {
            _hide = "true"
            handler.post(runnableCode)
            return
          }
        }
        if (getEventType(event) == "TYPE_VIEW_ACCESSIBILITY_FOCUSED") {
          _eventTime = event.eventTime.toString()
          _hide = "false"
          _packageName = packageName
          handler.post(runnableCode)
        }

        parentNodeInfo.recycle()
      }

      return
    }
  }

  override fun onInterrupt() {
    sendEventFromAccessibilityServicePermission("false")
  }

  override fun onDestroy() {
    super<AccessibilityService>.onDestroy()

    sendEventFromAccessibilityServicePermission("false")
    handler.removeCallbacks(runnableCode)
  }
}
