package com.dylphiiee.pieboard.ui

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.dylphiiee.pieboard.R
import com.dylphiiee.pieboard.data.SoundEntity
import com.dylphiiee.pieboard.databinding.DialogAddEditSoundBinding

/**
 * Bottom-sheet-like centered dialog to add a new sound or edit an existing one.
 * Uses ACTION_OPEN_DOCUMENT via GetContent so no extra storage permission is
 * strictly required to read the picked file (a temporary read grant is issued).
 */
class AddEditSoundDialog : DialogFragment() {

    private var _binding: DialogAddEditSoundBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    private var editingSound: SoundEntity? = null
    private var pickedUri: Uri? = null

    private val pickAudioLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                pickedUri = uri
                requireContext().contentResolver.takePersistableUriPermissionSafely(uri)
                binding.tvFileName.text = uri.lastPathSegmentOrName()
            }
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        _binding = DialogAddEditSoundBinding.inflate(LayoutInflater.from(requireContext()))
        dialog.setContentView(binding.root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val soundId = arguments?.getLong(ARG_SOUND_ID, -1L) ?: -1L

        if (soundId != -1L) {
            binding.tvDialogTitle.setText(R.string.edit_sound)
            viewModel.getSoundById(soundId) { sound ->
                editingSound = sound
                sound?.let {
                    binding.etName.setText(it.name)
                    binding.tvFileName.text = it.filePath.substringAfterLast('/')
                }
            }
        }

        binding.btnPickFile.setOnClickListener {
            pickAudioLauncher.launch("audio/*")
        }

        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnSave.setOnClickListener {
            val name = binding.etName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                binding.tilName.error = "Nama tidak boleh kosong"
                return@setOnClickListener
            }
            if (editingSound == null && pickedUri == null) {
                Toast.makeText(requireContext(), R.string.no_file_selected, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val existing = editingSound
            if (existing != null) {
                viewModel.updateSound(existing, name, pickedUri)
            } else {
                viewModel.addSound(name, pickedUri!!)
            }
            dismiss()
        }

        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SOUND_ID = "arg_sound_id"

        fun newInstanceForAdd(): AddEditSoundDialog = AddEditSoundDialog()

        fun newInstanceForEdit(soundId: Long): AddEditSoundDialog {
            return AddEditSoundDialog().apply {
                arguments = Bundle().apply { putLong(ARG_SOUND_ID, soundId) }
            }
        }
    }
}

private fun Uri.lastPathSegmentOrName(): String = this.lastPathSegment ?: this.toString()

private fun android.content.ContentResolver.takePersistableUriPermissionSafely(uri: Uri) {
    try {
        takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } catch (_: SecurityException) {
        // Some providers (e.g. GetContent from certain apps) don't support persistable
        // permissions; the file is copied immediately after picking so this is safe to ignore.
    }
}
