package com.example.minilauncher

import android.app.Activity
import android.app.ActivityOptions
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var list: RecyclerView
    private lateinit var bar: AlphabetBar
    private lateinit var bubble: TextView
    private lateinit var adapter: AppAdapter
    private val executor = Executors.newSingleThreadExecutor()
    private var firstLoad = true

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = loadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setContentView(R.layout.activity_main)
        list = findViewById(R.id.list)
        bar = findViewById(R.id.bar)
        bubble = findViewById(R.id.bubble)

        adapter = AppAdapter(::launch, ::showMenu)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.itemAnimator = null
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) = updateEdgeFade()
        })

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val b = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            list.setPadding(0, b.top + dp(12f), 0, b.bottom + dp(48f))
            bar.setPadding(0, b.top + dp(24f), 0, b.bottom + dp(24f))
            insets
        }

        bar.listener = object : AlphabetBar.Listener {
            override fun onLetter(letter: String) {
                bubble.text = letter
                (list.layoutManager as LinearLayoutManager)
                    .scrollToPositionWithOffset(adapter.positionOfLetter(letter), 0)
                list.post { updateEdgeFade() }
            }

            override fun onTouch(active: Boolean) {
                bubble.animate()
                    .alpha(if (active) 1f else 0f)
                    .scaleX(if (active) 1f else 0.85f)
                    .scaleY(if (active) 1f else 0.85f)
                    .setDuration(180)
                    .start()
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        loadApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(packageReceiver)
        executor.shutdown()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home pressed while already on the launcher: glide back to the top.
        list.smoothScrollToPosition(0)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        list.smoothScrollToPosition(0)
    }

    private fun loadApps() {
        executor.execute {
            val pm = packageManager
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(query, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map { ri ->
                    val label = ri.loadLabel(pm).toString()
                    AppEntry(
                        label = label,
                        pkg = ri.activityInfo.packageName,
                        component = android.content.ComponentName(
                            ri.activityInfo.packageName, ri.activityInfo.name
                        ),
                        icon = ri.loadIcon(pm),
                        letter = letterOf(label)
                    )
                }
                .sortedWith(Comparator { a, b -> a.label.compareTo(b.label, ignoreCase = true) })

            runOnUiThread {
                adapter.submit(apps)
                bar.letters = apps.map { it.letter }.distinct()
                if (firstLoad) {
                    firstLoad = false
                    list.alpha = 0f
                    list.translationY = dp(32f).toFloat()
                    list.animate().alpha(1f).translationY(0f)
                        .setDuration(450)
                        .setInterpolator(DecelerateInterpolator(2f))
                        .start()
                }
                list.post { updateEdgeFade() }
            }
        }
    }

    private fun letterOf(label: String): String {
        val c = label.trim().firstOrNull()?.uppercaseChar() ?: return "#"
        return if (c.isLetter()) c.toString() else "#"
    }

    /** Items fade out smoothly near the top and bottom edges. */
    private fun updateEdgeFade() {
        val h = list.height.toFloat()
        if (h == 0f) return
        val edge = dp(120f).toFloat()
        for (i in 0 until list.childCount) {
            val c = list.getChildAt(i)
            val cy = (c.top + c.bottom) / 2f
            val f = (minOf(cy, h - cy) / edge).coerceIn(0f, 1f)
            c.alpha = 0.25f + 0.75f * f
        }
    }

    private fun launch(v: View, e: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(e.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val opts = ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.width, v.height)
        try {
            startActivity(intent, opts.toBundle())
        } catch (_: Exception) {
        }
    }

    private fun showMenu(v: View, e: AppEntry) {
        val menu = PopupMenu(this, v)
        menu.menu.add(0, 1, 0, getString(R.string.app_info))
        menu.menu.add(0, 2, 1, getString(R.string.uninstall))
        menu.setOnMenuItemClickListener { item ->
            val uri = Uri.parse("package:${e.pkg}")
            try {
                when (item.itemId) {
                    1 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                    2 -> startActivity(Intent(Intent.ACTION_DELETE, uri))
                }
            } catch (_: Exception) {
            }
            true
        }
        menu.show()
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()
}
