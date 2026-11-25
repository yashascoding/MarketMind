package com.example.marketmind.ui.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.marketmind.R
import com.example.marketmind.databinding.ItemAllocationBinding
import com.example.marketmind.domain.model.PortfolioAllocation
import java.util.Locale

class AllocationComparisonAdapter :
    ListAdapter<PortfolioAllocation, AllocationComparisonAdapter.AllocationViewHolder>(AllocationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AllocationViewHolder {
        val binding = ItemAllocationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AllocationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AllocationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AllocationViewHolder(private val binding: ItemAllocationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PortfolioAllocation) {
            val context = binding.root.context
            binding.tvAssetName.text = item.assetName
            binding.tvCurrentPct.text = String.format(Locale.US, "%.1f%%", item.currentPercentage)
            binding.tvSuggestedPct.text = String.format(Locale.US, "%.1f%%", item.suggestedPercentage)

            val diff = item.differencePercentage
            val diffText = String.format(Locale.US, "%+.1f%%", diff)
            binding.tvDifferencePct.text = diffText

            val colorRes = when {
                diff > 0 -> R.color.market_positive
                diff < 0 -> R.color.market_negative
                else -> R.color.market_neutral
            }
            val pillBgRes = when {
                diff > 0 -> R.drawable.bg_pill_positive
                diff < 0 -> R.drawable.bg_pill_negative
                else -> R.drawable.bg_chip
            }

            binding.tvDifferencePct.setTextColor(ContextCompat.getColor(context, colorRes))
            binding.layoutDiffPill.setBackgroundResource(pillBgRes)

            try {
                binding.viewColorIndicator.backgroundTintList =
                    ColorStateList.valueOf(Color.parseColor(item.colorHex))
            } catch (e: Exception) {
                // Fallback default color
            }
        }
    }

    private class AllocationDiffCallback : DiffUtil.ItemCallback<PortfolioAllocation>() {
        override fun areItemsTheSame(
            oldItem: PortfolioAllocation,
            newItem: PortfolioAllocation
        ): Boolean {
            return oldItem.assetId == newItem.assetId
        }

        override fun areContentsTheSame(
            oldItem: PortfolioAllocation,
            newItem: PortfolioAllocation
        ): Boolean {
            return oldItem == newItem
        }
    }
}