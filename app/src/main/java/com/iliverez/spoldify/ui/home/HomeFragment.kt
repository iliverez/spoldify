package com.iliverez.spoldify.ui.home

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.iliverez.spoldify.R
import com.iliverez.spoldify.SpoldifyApp
import com.iliverez.spoldify.data.model.Album
import com.iliverez.spoldify.data.model.Track
import com.iliverez.spoldify.data.repository.AuthRepository
import com.iliverez.spoldify.databinding.FragmentHomeBinding
import com.iliverez.spoldify.ui.common.adapters.AlbumGridAdapter
import com.iliverez.spoldify.ui.common.adapters.TrackListAdapter

class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HomeViewModel by viewModels()

    private lateinit var recentlyPlayedAdapter: TrackListAdapter
    private lateinit var jumpBackInAdapter: AlbumGridAdapter
    private lateinit var madeForYouAdapter: AlbumGridAdapter
    private lateinit var radioMixAdapter: AlbumGridAdapter
    private lateinit var newReleasesAdapter: AlbumGridAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupListeners()
        observeViewModel()
        Log.i(TAG, "Spoldify build v2026.06.02 - HomeFragment created")
    }

    private fun setupRecyclerViews() {
        recentlyPlayedAdapter = TrackListAdapter { track ->
            playTrack(track)
        }
        binding.rvRecentlyPlayed.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecentlyPlayed.adapter = recentlyPlayedAdapter

        jumpBackInAdapter = AlbumGridAdapter { album ->
            navigateToAlbum(album)
        }
        binding.rvJumpBackIn.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvJumpBackIn.adapter = jumpBackInAdapter

        madeForYouAdapter = AlbumGridAdapter { album ->
            if (album.artistId.isNotBlank()) {
                navigateToAlbum(album)
            } else {
                val bundle = Bundle().apply { putString("playlistId", album.id) }
                findNavController().navigate(R.id.action_home_to_playlist, bundle)
            }
        }
        binding.rvMadeForYou.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvMadeForYou.adapter = madeForYouAdapter

        radioMixAdapter = AlbumGridAdapter { album ->
            navigateToAlbum(album)
        }
        binding.rvRadioMix.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvRadioMix.adapter = radioMixAdapter

        newReleasesAdapter = AlbumGridAdapter { album ->
            navigateToAlbum(album)
        }
        binding.rvNewReleases.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvNewReleases.adapter = newReleasesAdapter
    }

    private fun playTrack(track: Track) {
        SpoldifyApp.instance.playerWrapper.play("spotify:track:${track.id}")
        findNavController().navigate(R.id.action_home_to_now_playing)
    }

    private fun navigateToAlbum(album: Album) {
        val bundle = Bundle().apply { putString("albumId", album.id) }
        findNavController().navigate(R.id.action_home_to_album, bundle)
    }

    private fun generateQrBitmap(text: String, size: Int): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    private fun setupListeners() {
        binding.btnRetry.setOnClickListener {
            viewModel.loadHome()
        }

        binding.btnOAuthSignIn.setOnClickListener {
            val authRepository = SpoldifyApp.instance.authRepository
            val result = authRepository.startTokenExchange()

            if (result == null) {
                Toast.makeText(requireContext(), "No WiFi connection. Connect to WiFi first.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val qrBitmap = generateQrBitmap(result.setupUrl, 512)
            binding.ivQrCode.setImageBitmap(qrBitmap)
            binding.ivQrCode.visibility = View.VISIBLE

            binding.tvOAuthStatus.text = getString(R.string.oauth_scan_qr)
            binding.tvOAuthStatus.visibility = View.VISIBLE

            binding.tilAuthCode.visibility = View.VISIBLE
            binding.btnSubmitCode.visibility = View.VISIBLE

            Log.i(TAG, "Setup URL: ${result.setupUrl}")
            Log.i(TAG, "OAuth URL: ${result.oauthUrl}")

            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.oauthUrl)))
            } catch (e: Exception) {
                Log.w(TAG, "No browser available", e)
            }
        }

        binding.btnSubmitCode.setOnClickListener {
            val input = binding.etAuthCode.text?.toString()?.trim()
            if (input.isNullOrBlank()) return@setOnClickListener

            val code = extractCode(input)
            if (code == null) {
                Toast.makeText(requireContext(), "Could not find a code. Paste the full URL.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.tilAuthCode.visibility = View.GONE
            binding.btnSubmitCode.visibility = View.GONE

            val authRepository = SpoldifyApp.instance.authRepository
            if (!authRepository.hasPendingOAuthFlow()) {
                Toast.makeText(requireContext(), "Tap \"Continue with Spotify\" first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.tvOAuthStatus.text = getString(R.string.oauth_waiting)
            authRepository.swapAuthCodeForTokens(code)
        }
    }

    private fun extractCode(input: String): String? {
        try {
            val uri = Uri.parse(input)
            val code = uri.getQueryParameter("code")
            if (!code.isNullOrBlank()) return code
        } catch (_: Exception) {
        }
        return if (input.length > 20) input else null
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            if (isLoading) {
                binding.errorLayout.visibility = View.GONE
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Log.e(TAG, "Home error: $error")
                binding.progressBar.visibility = View.GONE
                binding.errorLayout.visibility = View.VISIBLE
                val isRateLimited = error.contains("rate limited", ignoreCase = true)
                        || error.contains("429", ignoreCase = true)
                binding.tvError.text = if (isRateLimited) {
                    getString(R.string.home_error_rate_limited)
                } else {
                    getString(R.string.home_error_loading)
                }
            }
        }

        SpoldifyApp.instance.authRepository.tokenUpgradedEvent.observe(viewLifecycleOwner) { timestamp ->
            if (timestamp != null && timestamp > 0) {
                Log.i(TAG, "Token upgraded via OAuth, clearing cache and reloading home")
                com.iliverez.spoldify.data.api.SpotifyApi.fromApp().clearRateLimit()
                viewModel.clearCacheAndReload()
            }
        }

        SpoldifyApp.instance.authRepository.tokenExchangeStatus.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AuthRepository.TokenExchangeState.Waiting -> {
                    binding.tvOAuthStatus.text = getString(R.string.oauth_waiting)
                    binding.tvOAuthStatus.visibility = View.VISIBLE
                }
                is AuthRepository.TokenExchangeState.Connected -> {
                    binding.tvOAuthStatus.text = getString(R.string.oauth_connected)
                    binding.tvOAuthStatus.visibility = View.VISIBLE
                    binding.ivQrCode.visibility = View.GONE
                    binding.tilAuthCode.visibility = View.GONE
                    binding.btnSubmitCode.visibility = View.GONE
                }
                is AuthRepository.TokenExchangeState.Error -> {
                    binding.tvOAuthStatus.text = "Error: ${state.message}"
                    binding.tvOAuthStatus.visibility = View.VISIBLE
                }
                null -> {}
            }
        }

        viewModel.recentlyPlayed.observe(viewLifecycleOwner) { tracks ->
            Log.d(TAG, "Recently played: ${tracks.size} tracks")
            recentlyPlayedAdapter.submitList(tracks)
            binding.sectionRecentlyPlayed.visibility = if (tracks.isNotEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.jumpBackIn.observe(viewLifecycleOwner) { albums ->
            Log.d(TAG, "Jump back in: ${albums.size} albums")
            jumpBackInAdapter.submitList(albums)
            binding.sectionJumpBackIn.visibility = if (albums.isNotEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.madeForYou.observe(viewLifecycleOwner) { albums ->
            Log.d(TAG, "Made for you: ${albums.size} items")
            madeForYouAdapter.submitList(albums)
            binding.sectionMadeForYou.visibility = if (albums.isNotEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.radioMix.observe(viewLifecycleOwner) { albums ->
            Log.d(TAG, "Radio mix: ${albums.size} items")
            radioMixAdapter.submitList(albums)
            binding.sectionRadioMix.visibility = if (albums.isNotEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.newReleases.observe(viewLifecycleOwner) { albums ->
            Log.d(TAG, "New releases: ${albums.size} items")
            newReleasesAdapter.submitList(albums)
            binding.sectionNewReleases.visibility = if (albums.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
