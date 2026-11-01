package com.azkar.app.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.azkar.app.R

/** Shown once after the last zikr of a collection is finished. */
class CompleteFragment : Fragment(R.layout.fragment_complete) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.folder).text = requireArguments().getString("name") ?: ""
        view.findViewById<View>(R.id.btn_home).setOnClickListener { nav().pop() }
    }

    companion object {
        fun of(name: String) = CompleteFragment().apply { arguments = bundleOf("name" to name) }
    }
}