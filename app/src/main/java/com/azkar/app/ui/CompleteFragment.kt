package com.azkar.app.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.azkar.app.R
import com.azkar.app.data.Tracking

/** Shown after the last zikr of a collection when the "Completion screen" setting is on. */
class CompleteFragment : Fragment(R.layout.fragment_complete) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.folder).text = requireArguments().getString("name") ?: ""
        // A tracked folder also shows its streak, which now includes today.
        val key = requireArguments().getString("key") ?: ""
        view.findViewById<TextView>(R.id.streak).apply {
            if (Tracking.isTracked(key)) {
                text = getString(R.string.complete_streak, Tracking.streak(listOf(key)))
                visibility = View.VISIBLE
            } else visibility = View.GONE
        }
        view.findViewById<View>(R.id.btn_home).setOnClickListener { nav().pop() }
    }

    companion object {
        fun of(name: String, key: String) = CompleteFragment().apply { arguments = Bundle().apply { putString("name", name); putString("key", key) } }
    }
}
