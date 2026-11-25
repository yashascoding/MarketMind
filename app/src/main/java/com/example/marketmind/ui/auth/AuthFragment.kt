package com.example.marketmind.ui.auth

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.provider.Settings
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.example.marketmind.R
import com.example.marketmind.databinding.FragmentAuthBinding

class AuthFragment : Fragment() {

    private var _binding: FragmentAuthBinding? = null
    private val binding get() = _binding!!

    private var isSignUp = false
    private var hasPlayedEntrance = false
    private var ambientAnimator: AnimatorSet? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnAuthSwitch.setOnClickListener { setSignUpMode(!isSignUp) }
        binding.btnAuthSubmit.setOnClickListener { submitCredentials() }
        binding.btnDemoMode.setOnClickListener { openDemoDashboard() }
        if (!hasPlayedEntrance && animationsEnabled()) {
            playEntranceAnimation()
            hasPlayedEntrance = true
        }
    }

    override fun onResume() {
        super.onResume()
        startAmbientAnimation()
    }

    override fun onPause() {
        ambientAnimator?.cancel()
        ambientAnimator = null
        super.onPause()
    }

    override fun onDestroyView() {
        ambientAnimator?.cancel()
        ambientAnimator = null
        _binding = null
        super.onDestroyView()
    }

    private fun setSignUpMode(signUp: Boolean) {
        isSignUp = signUp
        TransitionManager.beginDelayedTransition(binding.cardAuth, AutoTransition())
        binding.tilName.visibility = if (signUp) View.VISIBLE else View.GONE
        binding.tilConfirmPassword.visibility = if (signUp) View.VISIBLE else View.GONE
        binding.tvAuthTitle.setText(
            if (signUp) R.string.auth_signup_title else R.string.auth_login_title
        )
        binding.tvAuthSubtitle.setText(
            if (signUp) R.string.auth_signup_subtitle else R.string.auth_login_subtitle
        )
        binding.btnAuthSubmit.setText(
            if (signUp) R.string.auth_signup_submit else R.string.auth_login_action
        )
        binding.tvAuthSwitchPrompt.setText(
            if (signUp) R.string.auth_login_prompt else R.string.auth_signup_prompt
        )
        binding.btnAuthSwitch.setText(
            if (signUp) R.string.auth_login_action else R.string.auth_signup_action
        )
        clearValidationErrors()
        binding.tvAuthMessage.visibility = View.GONE
    }

    private fun submitCredentials() {
        if (!validateInputs()) {
            playErrorAnimation()
            return
        }

        binding.etPassword.text?.clear()
        binding.etConfirmPassword.text?.clear()
        binding.tvAuthMessage.setText(R.string.auth_unavailable_message)
        binding.tvAuthMessage.visibility = View.VISIBLE
    }

    private fun validateInputs(): Boolean {
        clearValidationErrors()
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()

        if (isSignUp && name.isBlank()) {
            binding.tilName.error = getString(R.string.auth_error_name_required)
            binding.etName.requestFocus()
            return false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.auth_error_email_invalid)
            binding.etEmail.requestFocus()
            return false
        }
        if (password.length < 8) {
            binding.tilPassword.error = getString(R.string.auth_error_password_short)
            binding.etPassword.requestFocus()
            return false
        }
        if (isSignUp && password != binding.etConfirmPassword.text?.toString().orEmpty()) {
            binding.tilConfirmPassword.error = getString(R.string.auth_error_password_mismatch)
            binding.etConfirmPassword.requestFocus()
            return false
        }
        return true
    }

    private fun clearValidationErrors() {
        binding.tilName.error = null
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null
    }

    private fun openDemoDashboard() {
        Toast.makeText(requireContext(), R.string.auth_demo_toast, Toast.LENGTH_SHORT).show()
        val options = NavOptions.Builder()
            .setPopUpTo(R.id.navigation_auth, true)
            .build()
        findNavController().navigate(R.id.action_auth_to_dashboard, null, options)
    }

    private fun playEntranceAnimation() {
        binding.ivBrandLogo.apply {
            alpha = 0f
            scaleX = 0.78f
            scaleY = 0.78f
            rotation = -12f
        }
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.ivBrandLogo, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(binding.ivBrandLogo, View.SCALE_X, 0.78f, 1f),
                ObjectAnimator.ofFloat(binding.ivBrandLogo, View.SCALE_Y, 0.78f, 1f),
                ObjectAnimator.ofFloat(binding.ivBrandLogo, View.ROTATION, -12f, 0f)
            )
            duration = 720
            interpolator = OvershootInterpolator(0.65f)
            start()
        }
        binding.cardAuth.apply {
            alpha = 0f
            translationY = 24f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(520)
                .setStartDelay(160)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun startAmbientAnimation() {
        if (!animationsEnabled() || ambientAnimator != null || _binding == null) return
        val cyanOrbAnimation =
            ObjectAnimator.ofFloat(binding.orbCyan, View.TRANSLATION_Y, -5f, 9f).apply {
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
            }
        val blueOrbAnimation =
            ObjectAnimator.ofFloat(binding.orbBlue, View.TRANSLATION_Y, 7f, -7f).apply {
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
            }
        ambientAnimator = AnimatorSet().apply {
            playTogether(cyanOrbAnimation, blueOrbAnimation)
            duration = 3_600
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun playErrorAnimation() {
        if (!animationsEnabled()) return
        ObjectAnimator.ofFloat(
            binding.cardAuth,
            View.TRANSLATION_X,
            0f,
            -7f,
            7f,
            -4f,
            4f,
            0f
        ).apply {
            duration = 280
            start()
        }
    }

    private fun animationsEnabled(): Boolean {
        return Settings.Global.getFloat(
            requireContext().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f
    }
}
