package com.dylphiiee.pieboard.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.dylphiiee.pieboard.data.SoundEntity
import com.dylphiiee.pieboard.databinding.ActivityMainBinding
import com.dylphiiee.pieboard.service.FloatingService
import com.dylphiiee.pieboard.util.SoundPlayer
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var adapter: SoundAdapter
    private lateinit var soundPlayer: SoundPlayer

    private var allSounds: List<SoundEntity> = emptyList()
    private var currentPage: Int = 0
    private val pageSize = 6

    private val overlaySettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) {
                proceedEnableFloating()
            } else {
                binding.switchFloating.isChecked = false
            }
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            startFloatingServiceInternal()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        soundPlayer = SoundPlayer(this, viewModel.prefs)

        setupList()
        setupVolume()
        setupFloatingSwitch()
        setupFab()
        observeSounds()
    }

    override fun onResume() {
        super.onResume()
        // Keep switch state honest if the user revoked overlay permission from system settings.
        if (viewModel.prefs.floatingEnabled && !Settings.canDrawOverlays(this)) {
            viewModel.prefs.floatingEnabled = false
        }
        binding.switchFloating.isChecked = viewModel.prefs.floatingEnabled
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPlayer.release()
    }

    private fun setupList() {
        adapter = SoundAdapter(
            onPlay = { soundPlayer.play(it.filePath) },
            onEdit = { AddEditSoundDialog.newInstanceForEdit(it.id).show(supportFragmentManager, "edit_sound") },
            onDelete = { confirmDelete(it) },
            onToggleFavorite = { viewModel.toggleFavorite(it) }
        )
        binding.rvSounds.layoutManager = LinearLayoutManager(this)
        binding.rvSounds.adapter = adapter

        binding.btnPrevPage.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                renderPage()
            }
        }
        binding.btnNextPage.setOnClickListener {
            val totalPages = totalPageCount()
            if (currentPage < totalPages - 1) {
                currentPage++
                renderPage()
            }
        }

        binding.etSearch.addTextChangedListener { text ->
            viewModel.updateSearchQuery(text?.toString().orEmpty())
            currentPage = 0
        }
    }

    private fun setupVolume() {
        binding.sliderVolume.value = viewModel.prefs.masterVolume.toFloat()
        binding.tvVolumeValue.text = "${viewModel.prefs.masterVolume}%"
        binding.sliderVolume.addOnChangeListener { _, value, _ ->
            val v = value.toInt()
            viewModel.prefs.masterVolume = v
            binding.tvVolumeValue.text = "$v%"
        }
    }

    private fun setupFloatingSwitch() {
        binding.switchFloating.isChecked = viewModel.prefs.floatingEnabled
        binding.switchFloating.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                enableFloatingButton()
            } else {
                disableFloatingButton()
            }
        }
    }

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            AddEditSoundDialog.newInstanceForAdd().show(supportFragmentManager, "add_sound")
        }
    }

    private fun observeSounds() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filteredSounds.collect { list ->
                    allSounds = list
                    val maxPage = max(0, totalPageCount() - 1)
                    if (currentPage > maxPage) currentPage = maxPage
                    renderPage()
                }
            }
        }
    }

    private fun totalPageCount(): Int =
        if (allSounds.isEmpty()) 0 else ceil(allSounds.size / pageSize.toDouble()).toInt()

    private fun renderPage() {
        if (allSounds.isEmpty()) {
            binding.tvEmptyState.text = if (viewModel.searchQuery.value.isBlank()) {
                getString(com.dylphiiee.pieboard.R.string.empty_state)
            } else {
                getString(com.dylphiiee.pieboard.R.string.search_empty_state)
            }
            binding.tvEmptyState.visibility = android.view.View.VISIBLE
            binding.paginationContainer.visibility = android.view.View.GONE
            adapter.submitList(emptyList())
            return
        }
        binding.tvEmptyState.visibility = android.view.View.GONE

        val totalPages = totalPageCount()
        val start = currentPage * pageSize
        val end = min(start + pageSize, allSounds.size)
        adapter.submitList(allSounds.subList(start, end))

        binding.paginationContainer.visibility =
            if (totalPages > 1) android.view.View.VISIBLE else android.view.View.GONE
        binding.tvPageIndicator.text = getString(
            com.dylphiiee.pieboard.R.string.page_indicator,
            currentPage + 1,
            max(1, totalPages)
        )
        binding.btnPrevPage.isEnabled = currentPage > 0
        binding.btnNextPage.isEnabled = currentPage < totalPages - 1
        binding.btnPrevPage.alpha = if (binding.btnPrevPage.isEnabled) 1f else 0.4f
        binding.btnNextPage.alpha = if (binding.btnNextPage.isEnabled) 1f else 0.4f
    }

    private fun confirmDelete(sound: SoundEntity) {
        AlertDialog.Builder(this)
            .setTitle(com.dylphiiee.pieboard.R.string.delete_sound_title)
            .setMessage(com.dylphiiee.pieboard.R.string.delete_sound_message)
            .setNegativeButton(com.dylphiiee.pieboard.R.string.cancel, null)
            .setPositiveButton(com.dylphiiee.pieboard.R.string.delete) { _, _ ->
                viewModel.deleteSound(sound)
            }
            .show()
    }

    // ---- Floating button enable/disable flow ----

    private fun enableFloatingButton() {
        if (!Settings.canDrawOverlays(this)) {
            showOverlayRationale()
            return
        }
        proceedEnableFloating()
    }

    private fun showOverlayRationale() {
        AlertDialog.Builder(this)
            .setTitle(com.dylphiiee.pieboard.R.string.overlay_permission_title)
            .setMessage(com.dylphiiee.pieboard.R.string.overlay_permission_message)
            .setCancelable(false)
            .setNegativeButton(com.dylphiiee.pieboard.R.string.cancel) { _, _ ->
                binding.switchFloating.isChecked = false
            }
            .setPositiveButton(com.dylphiiee.pieboard.R.string.grant_permission) { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlaySettingsLauncher.launch(intent)
            }
            .show()
    }

    private fun proceedEnableFloating() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        startFloatingServiceInternal()
    }

    private fun startFloatingServiceInternal() {
        viewModel.prefs.floatingEnabled = true
        binding.switchFloating.isChecked = true
        val intent = Intent(this, FloatingService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun disableFloatingButton() {
        viewModel.prefs.floatingEnabled = false
        stopService(Intent(this, FloatingService::class.java))
    }
}
