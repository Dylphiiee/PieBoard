package com.dylphiiee.pieboard.service

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.dylphiiee.pieboard.R
import com.dylphiiee.pieboard.data.SoundEntity
import com.dylphiiee.pieboard.databinding.ItemFloatingSoundBinding

class FloatingSoundAdapter(
    private var items: List<SoundEntity>,
    private val onClick: (SoundEntity) -> Unit,
    private val onToggleFavorite: (SoundEntity) -> Unit
) : RecyclerView.Adapter<FloatingSoundAdapter.VH>() {

    fun submitList(newItems: List<SoundEntity>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemFloatingSoundBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.binding.tvPanelSoundName.text = item.name
        holder.binding.tileClickArea.setOnClickListener { onClick(item) }

        holder.binding.ivPanelFavorite.setImageResource(
            if (item.isFavorite) R.drawable.ic_star_filled else R.drawable.ic_star_outline
        )
        holder.binding.ivPanelFavorite.setOnClickListener { onToggleFavorite(item) }
    }

    override fun getItemCount(): Int = items.size

    class VH(val binding: ItemFloatingSoundBinding) : RecyclerView.ViewHolder(binding.root)
}
