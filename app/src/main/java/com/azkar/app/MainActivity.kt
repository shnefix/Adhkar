package com.azkar.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.azkar.app.data.Store
import com.azkar.app.ui.HomeFragment
import com.azkar.app.ui.LibraryFragment
import com.azkar.app.ui.MyAzkarFragment
import com.azkar.app.ui.ReaderFragment
import com.azkar.app.ui.Tab

class MainActivity : AppCompatActivity() {
    private var currentTab: Tab = Tab.HOME
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentTab = savedInstanceState?.getInt("tab")?.let { Tab.values().getOrNull(it) } ?: Tab.HOME
        Store.init(this)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            showRoot(HomeFragment())
            handleIntent(intent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", currentTab.ordinal)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val key = i?.getStringExtra("open") ?: return
        i.removeExtra("open")
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
        showRoot(
            when (tab) {
                Tab.HOME -> HomeFragment()
                Tab.LIBRARY -> LibraryFragment()
                Tab.MY -> MyAzkarFragment()
            }
        )
    }

    fun push(f: Fragment) {
        supportFragmentManager.beginTransaction().replace(R.id.container, f).addToBackStack(null).commit()
    }

    fun pop() {
        android.util.Log.d("AzkarNav", "pop() called from:", Throwable())
        supportFragmentManager.popBackStack()
    }

    /** Resets the back stack and shows Home. */
    fun goHome() {
        android.util.Log.d("AzkarNav", "goHome() called from:", Throwable())
        currentTab = Tab.HOME
        showRoot(HomeFragment())
    }
}
