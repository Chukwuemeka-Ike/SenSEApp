package com.circadian.sense.ui.home

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.Fragment
import com.circadian.sense.R
import com.circadian.sense.databinding.FragmentHomeBinding


class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"
    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val circadianHealthButton = binding.circadianHealthButton
        val dataUseButton = binding.dataUseButton
        val whoWeAreButton = binding.whoWeAreButton
        circadianHealthButton.setOnClickListener { showPopupWindow(it) }
        dataUseButton.setOnClickListener { showPopupWindow(it) }
        whoWeAreButton.setOnClickListener { showPopupWindow(it) }

        return root
    }

    private fun showPopupWindow(v: View) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)
        when (v.id) {
            R.id.circadian_health_button -> {
                dialog.setContentView(R.layout.popup_circadian_health)
            }
            R.id.who_we_are_button -> {
                dialog.setContentView(R.layout.popup_who_we_are)
            }
            R.id.data_use_button -> {
                dialog.setContentView(R.layout.popup_data_use)
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}