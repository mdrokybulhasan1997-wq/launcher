package com.example.customdocklauncher

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val prefsName = "dock_prefs"
    private lateinit var slotButtons: Array<ImageButton>

    data class AppInfo(val label: String, val packageName: String, val icon: Drawable)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        slotButtons = arrayOf(
            findViewById(R.id.dock_slot_0),
            findViewById(R.id.dock_slot_1),
            findViewById(R.id.dock_slot_2),
            findViewById(R.id.dock_slot_3)
        )

        for (i in slotButtons.indices) {
            slotButtons[i].setOnClickListener { onSlotClicked(i) }
            slotButtons[i].setOnLongClickListener { onSlotLongClicked(i) }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        for (i in slotButtons.indices) refreshSlot(i)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // ---------- Immersive mode: hides status bar + nav bar ----------
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }
    }

    // ---------- Slot storage ----------
    private fun loadSlot(index: Int): String? {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        return prefs.getString("slot_$index", null)
    }

    private fun saveSlot(index: Int, packageName: String) {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().putString("slot_$index", packageName).apply()
    }

    private fun clearSlot(index: Int) {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().remove("slot_$index").apply()
    }

    // ---------- Slot UI ----------
    private fun refreshSlot(index: Int) {
        val pkg = loadSlot(index)
        val button = slotButtons[index]
        if (pkg == null) {
            button.setImageResource(android.R.drawable.ic_input_add)
            button.contentDescription = getString(R.string.empty_slot)
            return
        }
        try {
            val icon = packageManager.getApplicationIcon(pkg)
            button.setImageDrawable(icon)
            button.contentDescription = pkg
        } catch (e: Exception) {
            // App was uninstalled since it was added — reset slot
            clearSlot(index)
            button.setImageResource(android.R.drawable.ic_input_add)
        }
    }

    private fun onSlotClicked(index: Int) {
        val pkg = loadSlot(index)
        if (pkg == null) {
            showAppPicker(index)
            return
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, "App not found, removing from dock", Toast.LENGTH_SHORT).show()
            clearSlot(index)
            refreshSlot(index)
        }
    }

    private fun onSlotLongClicked(index: Int): Boolean {
        val pkg = loadSlot(index) ?: return false
        val options = arrayOf(getString(R.string.replace), getString(R.string.remove), getString(R.string.cancel))
        AlertDialog.Builder(this)
            .setTitle(pkg)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showAppPicker(index)
                    1 -> {
                        clearSlot(index)
                        refreshSlot(index)
                    }
                }
                dialog.dismiss()
            }
            .show()
        return true
    }

    // ---------- App picker ----------
    private fun getInstalledApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)
        return resolveInfos
            .map {
                AppInfo(
                    label = it.loadLabel(packageManager).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(packageManager)
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun showAppPicker(index: Int) {
        val apps = getInstalledApps()
        val adapter = AppListAdapter(this, apps)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.pick_app)
            .setAdapter(adapter) { d, which ->
                saveSlot(index, apps[which].packageName)
                refreshSlot(index)
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
    }

    private class AppListAdapter(
        private val context: Context,
        private val apps: List<AppInfo>
    ) : BaseAdapter() {

        override fun getCount(): Int = apps.size
        override fun getItem(position: Int): Any = apps[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val pad = (12 * context.resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)

                val icon = ImageView(context).apply {
                    id = 1001
                    layoutParams = LinearLayout.LayoutParams(
                        (40 * context.resources.displayMetrics.density).toInt(),
                        (40 * context.resources.displayMetrics.density).toInt()
                    )
                }
                addView(icon)

                val label = TextView(context).apply {
                    id = 1002
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = pad; gravity = android.view.Gravity.CENTER_VERTICAL }
                    textSize = 16f
                }
                addView(label)
            }

            val app = apps[position]
            (row as LinearLayout)
            (row.findViewById<ImageView>(1001)).setImageDrawable(app.icon)
            (row.findViewById<TextView>(1002)).text = app.label
            return row
        }
    }
}
