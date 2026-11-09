package com.flyre.lyrics.ui.adapter

import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.flyre.lyrics.R
import com.flyre.lyrics.data.model.LyricLine
import com.flyre.lyrics.databinding.ItemLyricLineBinding

class LyricsAdapter(
    private var fontSizeSp: Float = 16f
) : RecyclerView.Adapter<LyricsAdapter.LyricViewHolder>() {

    private val lines = mutableListOf<LyricLine>()
    private var activeIndex = -1

    fun submitLines(newLines: List<LyricLine>) {
        lines.clear()
        lines.addAll(newLines)
        activeIndex = -1
        notifyDataSetChanged()
    }

    fun setFontSize(sizeSp: Float) {
        if (fontSizeSp != sizeSp) {
            fontSizeSp = sizeSp
            notifyDataSetChanged()
        }
    }

    fun setActiveIndex(newIndex: Int): Boolean {
        if (newIndex == activeIndex || newIndex !in lines.indices) {
            return false
        }
        val previousIndex = activeIndex
        activeIndex = newIndex

        if (previousIndex in lines.indices) {
            notifyItemChanged(previousIndex)
        }
        notifyItemChanged(activeIndex)
        return true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LyricViewHolder {
        val binding = ItemLyricLineBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LyricViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LyricViewHolder, position: Int) {
        holder.bind(lines[position], position == activeIndex, fontSizeSp)
    }

    override fun getItemCount(): Int = lines.size

    class LyricViewHolder(
        private val binding: ItemLyricLineBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(line: LyricLine, isActive: Boolean, fontSizeSp: Float) {
            val context = binding.root.context
            binding.tvLyricText.text = line.text
            binding.tvLyricText.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)

            if (isActive) {
                binding.tvLyricText.setTextColor(ContextCompat.getColor(context, R.color.text_highlight))
                binding.tvLyricText.setTypeface(null, Typeface.BOLD)
                binding.tvLyricText.alpha = 1.0f
                binding.tvLyricText.scaleX = 1.05f
                binding.tvLyricText.scaleY = 1.05f
            } else {
                binding.tvLyricText.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                binding.tvLyricText.setTypeface(null, Typeface.NORMAL)
                binding.tvLyricText.alpha = 0.65f
                binding.tvLyricText.scaleX = 1.0f
                binding.tvLyricText.scaleY = 1.0f
            }
        }
    }
}

