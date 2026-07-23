package com.dylphiiee.pieboard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dylphiiee.pieboard.R
import com.dylphiiee.pieboard.data.SoundEntity
import com.dylphiiee.pieboard.databinding.ItemSoundBinding

class SoundAdapter(
    private val onPlay: (SoundEntity) -> Unit,
    private val onEdit: (SoundEntity) -> Unit,
    private val onDelete: (SoundEntity) -> Unit,
    private val onToggleFavorite: (SoundEntity) -> Unit
) : ListAdapter<SoundEntity, SoundAdapter.SoundViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SoundViewHolder {
        val binding = ItemSoundBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SoundViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SoundViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SoundViewHolder(private val binding: ItemSoundBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(sound: SoundEntity) {
            binding.tvName.text = sound.name
            binding.btnPlay.setOnClickListener { onPlay(sound) }
            binding.btnEdit.setOnClickListener { onEdit(sound) }
            binding.btnDelete.setOnClickListener { onDelete(sound) }

            binding.btnFavorite.setImageResource(
                if (sound.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            binding.btnFavorite.setOnClickListener { onToggleFavorite(sound) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SoundEntity>() {
            override fun areItemsTheSame(oldItem: SoundEntity, newItem: SoundEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: SoundEntity, newItem: SoundEntity) =
                oldItem == newItem
        }
    }
}
