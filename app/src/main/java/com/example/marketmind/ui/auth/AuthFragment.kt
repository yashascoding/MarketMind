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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.example.marketmind.R
import com.example.marketmind.data.cache.SettingsDataStore
import com.example.marketmind.databinding.FragmentAuthBinding
import kotlinx.coroutines.launch

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
        binding.btnGoogleSignIn.setOnClickListener { signInWithGoogle() }
        binding.btnDemoMode.setOnClickListener { openDemoDashboard() }
        getFirebaseAuth()?.currentUser?.let { navigateToDashboard() }
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

        val auth = getFirebaseAuth()
        if (auth == null) {
            showAuthMessage(R.string.auth_firebase_setup_required)
            return
        }

        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        val task = if (isSignUp) {
            auth.createUserWithEmailAndPassword(email, password)
        } else {
            auth.signInWithEmailAndPassword(email, password)
        }

        binding.tvAuthMessage.visibility = View.GONE
        setSubmitting(true)
        task.addOnCompleteListener { result ->
            if (_binding == null) return@addOnCompleteListener
            if (!result.isSuccessful) {
                setSubmitting(false)
                showAuthMessage(authErrorMessage(result.exception))
                return@addOnCompleteListener
            }

            if (isSignUp) {
                val user = auth.currentUser
                if (user == null) {
                    setSubmitting(false)
                    showAuthMessage(R.string.auth_error_generic)
                    return@addOnCompleteListener
                }
                val profile = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()
                user.updateProfile(profile).addOnCompleteListener { profileResult ->
                    if (_binding == null) return@addOnCompleteListener
                    if (!profileResult.isSuccessful) {
                        Toast.makeText(
                            requireContext(),
                            R.string.auth_profile_name_update_failed,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    finishAuthentication()
                }
            } else {
                finishAuthentication()
            }
        }
    }

    private fun signInWithGoogle() {
        val auth = getFirebaseAuth()
        if (auth == null) {
            showAuthMessage(R.string.auth_firebase_setup_required)
            return
        }

        val webClientIdResource = resources.getIdentifier(
            "default_web_client_id",
            "string",
            requireContext().packageName
        )
        if (webClientIdResource == 0 || getString(webClientIdResource).isBlank()) {
            showAuthMessage(R.string.auth_google_setup_required)
            return
        }

        val context = requireContext()
        val webClientId = getString(webClientIdResource)
        binding.tvAuthMessage.visibility = View.GONE
        setSubmitting(true, googleSignIn = true)

        viewLifecycleOwner.lifecycleScope.launch {
            val credential = try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(webClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                CredentialManager.create(context).getCredential(context, request).credential
            } catch (_: GetCredentialCancellationException) {
                if (_binding != null) setSubmitting(false)
                return@launch
            } catch (_: GetCredentialException) {
                if (_binding == null) return@launch
                setSubmitting(false)
                showAuthMessage(R.string.auth_google_error)
                return@launch
            }

            if (_binding == null) return@launch
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                setSubmitting(false)
                showAuthMessage(R.string.auth_google_error)
                return@launch
            }

            val idToken = try {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } catch (_: GoogleIdTokenParsingException) {
                setSubmitting(false)
                showAuthMessage(R.string.auth_google_error)
                return@launch
            }

            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .addOnCompleteListener { result ->
                    if (_binding == null) return@addOnCompleteListener
                    if (!result.isSuccessful) {
                        setSubmitting(false)
                        showAuthMessage(
                            authErrorMessage(result.exception, googleSignIn = true)
                        )
                        return@addOnCompleteListener
                    }
                    finishAuthentication()
                }
        }
    }

    private fun getFirebaseAuth(): FirebaseAuth? {
        val app = FirebaseApp.getApps(requireContext())
            .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: return null
        return FirebaseAuth.getInstance(app)
    }

    private fun authErrorMessage(error: Exception?, googleSignIn: Boolean = false): Int {
        return when ((error as? FirebaseAuthException)?.errorCode) {
            "ERROR_INVALID_EMAIL" -> R.string.auth_error_email_invalid
            "ERROR_INVALID_CREDENTIAL",
            "ERROR_WRONG_PASSWORD",
            "ERROR_USER_NOT_FOUND" -> R.string.auth_error_credentials
            "ERROR_EMAIL_ALREADY_IN_USE" -> R.string.auth_error_email_in_use
            "ERROR_WEAK_PASSWORD" -> R.string.auth_error_password_weak
            "ERROR_NETWORK_REQUEST_FAILED" -> R.string.auth_error_network
            "ERROR_TOO_MANY_REQUESTS" -> R.string.auth_error_rate_limited
            "ERROR_OPERATION_NOT_ALLOWED" -> if (googleSignIn) {
                R.string.auth_google_provider_disabled
            } else {
                R.string.auth_error_provider_disabled
            }
            else -> R.string.auth_error_generic
        }
    }

    private fun setSubmitting(submitting: Boolean, googleSignIn: Boolean = false) {
        binding.btnAuthSubmit.isEnabled = !submitting
        binding.btnGoogleSignIn.isEnabled = !submitting
        binding.btnAuthSwitch.isEnabled = !submitting
        binding.btnDemoMode.isEnabled = !submitting
        binding.etName.isEnabled = !submitting
        binding.etEmail.isEnabled = !submitting
        binding.etPassword.isEnabled = !submitting
        binding.etConfirmPassword.isEnabled = !submitting
        binding.btnAuthSubmit.setText(
            when {
                submitting && googleSignIn && isSignUp -> R.string.auth_signup_submit
                submitting && googleSignIn -> R.string.auth_login_action
                submitting && isSignUp -> R.string.auth_creating_account
                submitting -> R.string.auth_signing_in
                isSignUp -> R.string.auth_signup_submit
                else -> R.string.auth_login_action
            }
        )
        binding.btnGoogleSignIn.setText(
            if (submitting && googleSignIn) {
                R.string.auth_google_signing_in
            } else {
                R.string.auth_google_action
            }
        )
    }

    private fun showAuthMessage(messageId: Int) {
        binding.tvAuthMessage.setText(messageId)
        binding.tvAuthMessage.visibility = View.VISIBLE
    }

    private fun finishAuthentication() {
        binding.etPassword.text?.clear()
        binding.etConfirmPassword.text?.clear()
        setSubmitting(false)
        navigateToDashboard()
    }

    private fun navigateToDashboard() {
        if (_binding == null || !isAdded) return
        val options = NavOptions.Builder()
            .setPopUpTo(R.id.navigation_auth, true)
            .setLaunchSingleTop(true)
            .build()
        findNavController().navigate(R.id.action_auth_to_dashboard, null, options)
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
        viewLifecycleOwner.lifecycleScope.launch {
            SettingsDataStore(requireContext()).saveDemoMode(true)
            Toast.makeText(requireContext(), R.string.auth_demo_toast, Toast.LENGTH_SHORT).show()
            navigateToDashboard()
        }
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
