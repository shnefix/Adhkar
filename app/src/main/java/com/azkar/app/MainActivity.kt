package com.azkar.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.azkar.app.data.AppPrefs
import com.azkar.app.data.Store
import com.azkar.app.reminders.LocationUpdater
import com.azkar.app.ui.HandlesOwnInsets
import com.azkar.app.ui.HomeFragment
import com.azkar.app.ui.LibraryFragment
import com.azkar.app.ui.ReaderFragment
import com.azkar.app.ui.Tab
import com.azkar.app.ui.safeBottom
import com.azkar.app.widgets.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
  
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var currentTab: Tab = Tab.HOME

    // Prayer-time reminders: approximate location is enough. Asked once; denying keeps the fixed times.
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) LocationUpdater.refresh(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentTab = savedInstanceState?.getInt("tab")?.let { Tab.values().getOrNull(it) } ?: Tab.HOME
        Store.init(this)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContentView(R.layout.activity_main)
        keepClearOfSystemBars()
        var contentShown = false
        lifecycleScope.launch {
            try {
                repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                    if (contentShown) return@repeatOnLifecycle
                    Store.awaitReady()
                    ThemeMode.apply(AppPrefs.theme())
                    if (Store.consumeRecoveryNotice()) {
                        Toast.makeText(this@MainActivity, R.string.data_recovered, Toast.LENGTH_LONG).show()
                    }
                    com.azkar.app.widgets.NewWidgetUpdater.syncTrackingProviders(this@MainActivity)
                    // The container is set in onCreate so restored fragments have somewhere to attach;
                    // show Home only when nothing was restored (fresh start or empty state).
                    if (supportFragmentManager.findFragmentById(R.id.container) == null) {
                        showRoot(HomeFragment())
                        handleIntent(intent)
                    }
                    contentShown = true
                    if (LocationUpdater.hasPermission(this@MainActivity)) LocationUpdater.refresh(this@MainActivity)
                    else if (!AppPrefs.locationAsked()) {
                        AppPrefs.setLocationAsked()
                        locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Azkar", "Unable to initialize application data", e)
                Toast.makeText(this@MainActivity, R.string.data_load_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Pads the fragment container so Android's navigation bar and the keyboard never cover a screen's
     * buttons. Screens marked [HandlesOwnInsets] get no padding and place their own bars.
     */
    private fun keepClearOfSystemBars() {
        val container = findViewById<View>(R.id.container)
        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            if (supportFragmentManager.findFragmentById(R.id.container) is HandlesOwnInsets) v.setPadding(0, 0, 0, 0)
            else insets.safeBottom().let { v.setPadding(it.left, 0, it.right, it.bottom) }
            insets
        }
        // The status bar is transparent: a scrim of its height keeps scrolled text from running under the clock.
        // In the reader the bars are hidden, so its height drops to 0.
        val scrim = findViewById<View>(R.id.status_scrim)
        ViewCompat.setOnApplyWindowInsetsListener(scrim) { v, insets ->
            val top = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
            if (v.layoutParams.height != top) v.layoutParams = v.layoutParams.apply { height = top }
            insets
        }
        // Insets are only dispatched on change, so re-apply them whenever a different screen is shown.
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                ViewCompat.requestApplyInsets(container)
                ViewCompat.requestApplyInsets(scrim)
            }
        }, false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", currentTab.ordinal)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lifecycleScope.launch {
            try {
                repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                    Store.awaitReady()
                    handleIntent(intent)
                    return@repeatOnLifecycle
                }
            } catch (e: Exception) {
                android.util.Log.e("Azkar", "Unable to handle application deep link", e)
            }
        }
    }

    private fun handleIntent(i: Intent?) {
        val key = i?.getStringExtra("open") ?: return
        i.removeExtra("open")
        currentTab = Tab.HOME
        showRoot(HomeFragment())
        if (Store.readerItems(key).isNotEmpty()) push(ReaderFragment.of(key))   // deleted or empty folder: just stay on Home
    }

    private fun showRoot(f: Fragment) {
        supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction().replace(R.id.container, f).commit()
    }

    fun openTab(tab: Tab) {
        if (tab == currentTab && supportFragmentManager.backStackEntryCount == 0) return
        currentTab = tab
        showRoot(rootOf(tab))
    }

    fun push(f: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.container, f).addToBackStack(null).commit()
    }

    fun pop() {
        supportFragmentManager.popBackStack()
    }

    /** The last zikr of a collection was finished: back to the tab it was opened from (Home or Library). */
    fun finishReading() {
        showRoot(rootOf(currentTab))
    }

    /** The tab the reader was opened from, so the completion screen can name where its button goes. */
    val readingTab: Tab get() = currentTab

    private fun rootOf(tab: Tab): Fragment = when (tab) {
        Tab.HOME -> HomeFragment()
        Tab.LIBRARY -> LibraryFragment()
        Tab.WIDGETS -> com.azkar.app.ui.WidgetEditorFragment()
    }
    /** Resets the back stack and shows Home. */
    fun goHome() {
        currentTab = Tab.HOME
        showRoot(HomeFragment())
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                Store.awaitReady()
                WidgetUpdater.updateAll(this@MainActivity)
            }.onFailure { android.util.Log.e("Azkar", "Widget refresh failed", it) }
        }
    }
}
