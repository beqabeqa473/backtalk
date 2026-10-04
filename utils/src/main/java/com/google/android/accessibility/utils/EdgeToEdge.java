/*
 * Copyright 2026 Backtalk contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.utils;

import android.app.Activity;
import android.view.View;
import android.view.Window;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public final class EdgeToEdge {
  private static final int SAFE_AREA =
      WindowInsetsCompat.Type.systemBars()
          | WindowInsetsCompat.Type.displayCutout()
          | WindowInsetsCompat.Type.ime();

  private EdgeToEdge() {}

  public static void fitToSafeArea(Activity activity) {
    View content = activity.findViewById(Window.ID_ANDROID_CONTENT);
    if (content == null || !(content.getParent() instanceof View root)) {
      return;
    }
    ViewCompat.setOnApplyWindowInsetsListener(
        root,
        (view, insets) -> {
          Insets safe = insets.getInsets(SAFE_AREA);
          view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
          return WindowInsetsCompat.CONSUMED;
        });
    ViewCompat.requestApplyInsets(root);
  }
}
