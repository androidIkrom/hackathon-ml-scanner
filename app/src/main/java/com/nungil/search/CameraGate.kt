package com.nungil.search

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.nungil.R
import com.nungil.contract.app.services
import com.nungil.databinding.SearchPermissionPanelBinding

/**
 * Camera permission for every Y camera screen. Create it as a Fragment property (it registers an
 * activity-result launcher), [attach] it in onViewCreated, call [check] in onResume, [detach] in onDestroyView.
 * Asks once per screen; after a refusal it shows the panel with an "Open app settings" button and says why,
 * once. It never loops, also with "don't ask again".
 */
class CameraGate(private val fragment: Fragment, private val onGranted: () -> Unit) {
    private var panel: SearchPermissionPanelBinding? = null
    private var asked = false
    private var started = false
    private var explained = false

    private val launcher = fragment.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) grant() else deny()
    }

    fun attach(panel: SearchPermissionPanelBinding) {
        this.panel = panel
        asked = false
        started = false
        explained = false
        panel.permissionSettings.setOnClickListener { openSettings() }
    }

    fun detach() {
        panel = null
    }

    fun check() {
        if (panel == null) return
        when {
            isGranted() -> grant()
            !asked -> {
                asked = true
                launcher.launch(Manifest.permission.CAMERA)
            }
            else -> deny()
        }
    }

    private fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(fragment.requireContext(), Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun grant() {
        val p = panel ?: return
        p.root.visibility = View.GONE
        if (!started) {
            started = true
            onGranted()
        }
    }

    private fun deny() {
        val p = panel ?: return
        p.root.visibility = View.VISIBLE
        if (!explained) {
            explained = true
            fragment.services().speaker.say(fragment.getString(R.string.search_camera_needed_body))
        }
    }

    private fun openSettings() {
        val uri = Uri.fromParts("package", fragment.requireContext().packageName, null)
        fragment.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
    }
}
