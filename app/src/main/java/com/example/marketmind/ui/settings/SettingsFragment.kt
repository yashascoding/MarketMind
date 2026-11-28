package com.example.marketmind.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.example.marketmind.R
import com.example.marketmind.databinding.FragmentSettingsBinding
import com.example.marketmind.ui.common.HapticFeedbackUtil
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        binding.btnSignOut.visibility =
            if (getFirebaseAuth()?.currentUser != null) View.VISIBLE else View.GONE
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnSaveUrl.setOnClickListener {
            HapticFeedbackUtil.performClickFeedback(it)
            val url = binding.etBaseUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                viewModel.saveBaseUrl(url)
                Toast.makeText(requireContext(), "Backend URL saved successfully.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnTestConnection.setOnClickListener {
            HapticFeedbackUtil.performClickFeedback(it)
            val url = binding.etBaseUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                viewModel.testConnection(url)
            }
        }

        binding.switchAutoRefresh.setOnCheckedChangeListener { _, isChecked ->
            viewModel.saveAutoRefresh(isChecked)
        }

        binding.switchDemoMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.saveDemoMode(isChecked)
        }

        binding.btnSignOut.setOnClickListener {
            val auth = getFirebaseAuth() ?: return@setOnClickListener
            if (auth.currentUser == null) return@setOnClickListener
            auth.signOut()
            val options = NavOptions.Builder()
                .setPopUpTo(R.id.navigation_dashboard, true)
                .setLaunchSingleTop(true)
                .build()
            findNavController().navigate(R.id.navigation_auth, null, options)
        }
    }

    private fun getFirebaseAuth(): FirebaseAuth? {
        val app = FirebaseApp.getApps(requireContext())
            .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: return null
        return FirebaseAuth.getInstance(app)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.baseUrl.collect { url ->
                        if (!binding.etBaseUrl.hasFocus()) {
                            binding.etBaseUrl.setText(url)
                        }
                    }
                }

                launch {
                    viewModel.autoRefresh.collect { enabled ->
                        binding.switchAutoRefresh.isChecked = enabled
                    }
                }

                launch {
                    viewModel.demoMode.collect { enabled ->
                        binding.switchDemoMode.isChecked = enabled
                    }
                }

                launch {
                    viewModel.connectionResult.collect { result ->
                        result?.let {
                            binding.tvConnectionResult.text = it
                        }
                    }
                }

                launch {
                    viewModel.isTesting.collect { isTesting ->
                        binding.btnTestConnection.isEnabled = !isTesting
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}