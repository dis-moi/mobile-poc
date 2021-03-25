package com.dismoi.scout.AccessibilityService;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.text.TextUtils;

import android.app.Activity;
import androidx.annotation.NonNull;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import android.content.Intent;
import android.net.Uri;

import javax.annotation.Nullable;

public class AccessibilityServiceModule extends ReactContextBaseJavaModule {

  private static ReactApplicationContext reactContext;

  public AccessibilityServiceModule(ReactApplicationContext reactContext) { 
    super(reactContext);
    
    AccessibilityServiceModule.reactContext = reactContext;
  }

  @NonNull
  @Override
  public String getName() {
    return "AccessibilityServiceModule";
  }


  private boolean isAccessibilitySettingsOn(Context mContext) {
    int accessibilityEnabled = 0;
    final String service = reactContext.getPackageName() + "/" + AccessibilityServiceModule.class.getCanonicalName();
    try {
      accessibilityEnabled = Settings.Secure.getInt(
              mContext.getApplicationContext().getContentResolver(),
              android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
    } catch (Settings.SettingNotFoundException e) {
    }
    TextUtils.SimpleStringSplitter mStringColonSplitter = new TextUtils.SimpleStringSplitter(':');

    if (accessibilityEnabled == 1) {
      String settingValue = Settings.Secure.getString(
              mContext.getApplicationContext().getContentResolver(),
              Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

      if (settingValue != null) {
        if (settingValue.indexOf(reactContext.getPackageName()) > -1) {
          return true;
        }
      }
    }

    return false;
  }

  @ReactMethod
  public void isAccessibilityEnabled(Callback callback) {
    if (!isAccessibilitySettingsOn(reactContext.getApplicationContext())) {
      callback.invoke("0", null);
    } else {
      callback.invoke("1", null);
      Activity currentActivity = getCurrentActivity();

      Intent startActivity = reactContext.getPackageManager()
         .getLaunchIntentForPackage(reactContext.getPackageName());
      startActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

      currentActivity.startActivity(startActivity);
    }
  }

  @ReactMethod
  public void redirectToAppAccessibilitySettings(final Promise promise) {
    Activity currentActivity = getCurrentActivity();

    currentActivity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
  }

  private static void sendEventToReactNative(
    ReactContext reactContext,
    String eventName,
    @Nullable String params
  ) {
    if (reactContext == null) {
      return;
    }

    reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params);
  }

  public static void prepareEventFromChromeURL(String params) {
    sendEventToReactNative(reactContext, "EventFromChromeURL", params);
  }

  public static void prepareEventFromAccessibilityServicePermission(String params) {
    sendEventToReactNative(reactContext, "EventFromAccessibilityServicePermission", params);
  }

  public static void prepareEventFromLeavingChromeApp(String params) {
    sendEventToReactNative(reactContext, "EventFromLeavingChromeApp", params);
  }
}
