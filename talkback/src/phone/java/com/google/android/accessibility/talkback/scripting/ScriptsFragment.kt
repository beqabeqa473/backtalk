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

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.PreferenceScreen
import com.google.android.accessibility.talkback.R
import java.io.IOException
import java.util.concurrent.Executors

class ScriptsFragment : ScriptScreenFragment() {
  private val openScript =
    registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      uri?.let(::importScript)
    }

  public override fun getTitle(): CharSequence = getText(R.string.title_pref_scripts)

  override fun state(): Any = store.all()

  override fun build(screen: PreferenceScreen) {
    screen.action(getString(R.string.script_import), getString(R.string.script_import_summary)) {
      openScript.launch(arrayOf("*/*"))
    }
    val (global, app) = store.all().partition { it.manifest.isGlobal }
    if (global.isEmpty() && app.isEmpty()) screen.info(getString(R.string.scripts_none))
    for ((title, scripts) in
      listOf(R.string.scripts_category_app to app, R.string.scripts_category_global to global)) {
      if (scripts.isEmpty()) continue
      val category = screen.category(getString(title))
      scripts.forEach {
        category.link(
          it.manifest.name,
          requireContext().scriptSummary(it),
          ScriptDetailFragment::class.java,
          it.id,
        )
      }
    }
  }

  private fun importScript(uri: Uri) {
    val resolver = requireContext().contentResolver
    executor.execute {
      val result = runCatching {
        val bytes =
          resolver.openInputStream(uri)?.use { it.readAtMost(ScriptPackage.MAX_BYTES + 1) }
            ?: throw IOException("The file could not be opened")
        ScriptPackage.read(bytes)
      }
      view?.post {
        if (isAdded) result.fold(::confirmInstall) { importFailed(it.message) }
      }
    }
  }

  private fun confirmInstall(pkg: ScriptPackage) {
    val context = requireContext()
    val translations =
      ScriptTranslations.load(ScriptTranslations.languageOf(context)) { pkg.files[it] }
    val manifest = pkg.manifest.translated(translations::text)
    val warning = manifest.compatibilityWarning(context)
    val existing = store.find(manifest.id)
    val added = manifest.permissions - existing?.manifest?.permissions.orEmpty()
    val granted = (existing?.granted.orEmpty() intersect manifest.permissions) + added
    val isNew = existing == null
    val done =
      if (isNew) getString(R.string.script_installed, manifest.name)
      else getString(R.string.script_updated, manifest.name, manifest.version)
    if (!isNew && added.isEmpty() && warning == null) return install(pkg, granted, done)
    val title =
      if (isNew) getString(R.string.script_install_title, manifest.name)
      else getString(R.string.script_update_title, manifest.name, manifest.version)
    val button =
      when {
        isNew && warning == null -> R.string.script_install_button
        isNew -> R.string.script_install_anyway
        warning == null -> R.string.script_update_button
        else -> R.string.script_update_anyway
      }
    val message = if (isNew) installMessage(manifest, warning) else updateMessage(added, warning)
    confirm(title, message, button) { install(pkg, granted, done) }
  }

  private fun installMessage(manifest: ScriptManifest, warning: String?): String {
    val context = requireContext()
    val permissions =
      if (manifest.permissions.isEmpty()) {
        getString(R.string.script_install_no_permissions)
      } else {
        listOf(
            getString(R.string.script_install_can),
            context.permissionsText(manifest.permissions),
            getString(R.string.script_install_permissions_note),
          )
          .joinToString("\n")
      }
    val commands =
      manifest.commands
        .takeIf { it.isNotEmpty() }
        ?.let { getString(R.string.script_install_adds) + "\n" + context.commandsText(manifest) }
    return listOfNotNull(warning, context.scriptAbout(manifest), permissions, commands)
      .joinToString("\n\n")
  }

  private fun updateMessage(added: Set<ScriptPermission>, warning: String?): String =
    listOfNotNull(
        warning,
        added
          .takeIf { it.isNotEmpty() }
          ?.let {
            getString(R.string.script_update_new_permissions) +
              "\n" +
              requireContext().permissionsText(it)
          },
      )
      .joinToString("\n\n")

  private fun install(pkg: ScriptPackage, granted: Set<ScriptPermission>, done: String) =
    try {
      store.install(pkg, granted)
      toast(done)
    } catch (e: IOException) {
      importFailed(e.message)
    }

  private fun importFailed(reason: String?) =
    toast(getString(R.string.script_import_failed, reason.orEmpty()))

  private companion object {
    val executor = Executors.newSingleThreadExecutor()
  }
}
