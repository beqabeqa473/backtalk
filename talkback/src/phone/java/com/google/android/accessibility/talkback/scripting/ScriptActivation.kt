/*
 * Copyright 2026 Backtalk contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.talkback.scripting

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import org.json.JSONObject

class ScriptActivation(private val service: AccessibilityService) {
  private var activePackage: String? = null
  private var focusedPackage: String? = null
  private val activityByPackage = HashMap<String, String>()

  var windowTitle: String? = null
    private set

  @Volatile
  var appInfo: JSONObject = JSONObject()
    private set

  val activity: String?
    get() = activePackage?.let(activityByPackage::get)

  fun onWindowStateChanged(event: AccessibilityEvent) {
    val packageName = event.packageName?.toString() ?: return
    val className = event.className?.toString() ?: return
    if (!className.startsWith("android.")) {
      activityByPackage[packageName] = className
    }
  }

  fun onFocusMoved(packageName: String?): Boolean =
    (packageName != focusedPackage).also { focusedPackage = packageName }

  fun refresh() {
    readActiveWindow()
    appInfo =
      jsonObject("package" to activePackage, "activity" to activity, "window" to windowTitle)
  }

  fun wants(script: InstalledScript): Boolean =
    script.enabled &&
      (script.manifest.isGlobal ||
        script.manifest.apps.any {
          it.matchesFront(activePackage, activity, windowTitle) ||
            (focusedPackage != activePackage && it.matchesOther(focusedPackage))
        })

  private fun readActiveWindow() {
    val windows = runCatching { service.windows.orEmpty() }.getOrDefault(emptyList())
    val active =
      windows.firstOrNull {
        it.isActive && it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
      } ?: windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION } ?: return
    activePackage = active.root?.packageName?.toString() ?: return
    windowTitle = active.title?.toString()
  }
}
