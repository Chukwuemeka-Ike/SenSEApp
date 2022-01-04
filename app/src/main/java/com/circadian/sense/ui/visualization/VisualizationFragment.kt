package com.circadian.sense.ui.visualization

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.circadian.sense.AuthManager
import com.circadian.sense.databinding.FragmentVisualizationBinding

class VisualizationFragment : Fragment() {

    private lateinit var visualizationViewModel: VisualizationViewModel
    private var _binding: FragmentVisualizationBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var authManager: AuthManager? = null
    val TAG = "SenSE Debug"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        visualizationViewModel =
            ViewModelProvider(this).get(VisualizationViewModel::class.java)

        _binding = FragmentVisualizationBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textVisualization
        visualizationViewModel.text.observe(viewLifecycleOwner, Observer {
            textView.text = it
        })

        authManager = context?.let { AuthManager(it) }
//        authManager?.requestUserData()
//        Log.i(TAG, "Request User Data Response: ${authManager?.dataRequestResponse}")

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}