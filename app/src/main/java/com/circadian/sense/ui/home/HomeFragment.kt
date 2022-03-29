package com.circadian.sense.ui.home

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.widget.PopupWindow
import androidx.annotation.TransitionRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.circadian.sense.R
import com.circadian.sense.databinding.FragmentHomeBinding


class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"
    private lateinit var homeViewModel: HomeViewModel
    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel =
            ViewModelProvider(this)[HomeViewModel::class.java]

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

//        val textView: TextView = binding.textHome
//        homeViewModel.text.observe(viewLifecycleOwner, Observer {
//            textView.text = getString(it)
//        })
        val circadianHealthButton = binding.circadianHealthButton
        val dataUseButton = binding.dataUseButton
        val whoWeAreButton = binding.whoWeAreButton

        circadianHealthButton.setOnClickListener { showPopupWindow(it, container) }
        dataUseButton.setOnClickListener { showPopupWindow(it, container) }
        whoWeAreButton.setOnClickListener { showPopupWindow(it, container) }

        return root
    }

    private fun showPopupWindow(v: View, container: ViewGroup?) {
        val popupWindow = PopupWindow()

        when (v.id){
            R.id.circadian_health_button -> {
                popupWindow.contentView = requireActivity().layoutInflater.inflate(R.layout.popup_circadian_health, container, false)
            }
            R.id.who_we_are_button -> {
                popupWindow.contentView = requireActivity().layoutInflater.inflate(R.layout.popup_who_we_are, container, false)
            }
            R.id.data_use_button ->{
                popupWindow.contentView = requireActivity().layoutInflater.inflate(R.layout.popup_data_use, container, false)
            }
        }

        popupWindow.width = ConstraintLayout.LayoutParams.MATCH_PARENT
        popupWindow.height = ConstraintLayout.LayoutParams.WRAP_CONTENT
        popupWindow.isFocusable = true
//        popupWindow.isTouchable = true
        popupWindow.isOutsideTouchable = true
//            popupWindow.enterTransition
        popupWindow.animationStyle = Animation.INFINITE
//        popupWindow.setBackgroundDrawable()
        popupWindow.showAtLocation(v.rootView, Gravity.CENTER, 0, 0)
//        popupWindow.showAsDropDown(v.rootView, 0, 0, Gravity.CENTER_VERTICAL)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}