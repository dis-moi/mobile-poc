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

  private val NOTIFICATION_TIMEOUT: Long = 800

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
    info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
      AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED or
      AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED 
      //or
      //AccessibilityEvent.TYPE_VIEW_FOCUSED

    //AccessibilityEvent.TYPE_WINDOWS_CHANGED or
    /*
      info.packageNames is not set because we want to receive event from
      all packages
     */

    info.feedbackType = AccessibilityServiceInfo.FEEDBACK_VISUAL
    info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS

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

  fun isLauncherActivated(packageName: String): Boolean {
    return packageName.contains("launcher")
  }

  /*
    This method is called back by the system when it detects an 
    AccessibilityEvent that matches the event filtering parameters 
    specified by your accessibility service
   */
  @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
  override fun onAccessibilityEvent(event: AccessibilityEvent) {
    if (rootInActiveWindow == null) {
      return
    }

    if (overlayIsActivated(applicationContext)) {
      val packageName = event.packageName.toString()

      if (getEventType(event) == "TYPE_WINDOW_STATE_CHANGED" && packageName != "com.android.chrome") {
        if (packageName.contains("com.google.android.inputmethod") ||
          packageName == "com.google.android.googlequicksearchbox" ||
          packageName == "com.android.systemui"
        ) {
          _eventTime = event.eventTime.toString()
          _packageName = packageName
          _hide = "true"
          handler.post(runnableCode)
          return
        }
      }

      if (isLauncherActivated(packageName)) {
        _eventTime = event.eventTime.toString()
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
            _eventTime = event.eventTime.toString()
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
