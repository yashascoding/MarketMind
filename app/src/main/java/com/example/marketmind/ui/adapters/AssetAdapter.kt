package com.example.marketmind.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.marketmind.R
import com.example.marketmind.databinding.ItemAssetCardBinding
import com.example.marketmind.domain.model.MarketAsset
import com.example.marketmind.ui.common.Formatters

class AssetAdapter : ListAdapter<MarketAsset, AssetAdapter.AssetViewHolder>(AssetDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AssetViewHolder {
        val binding = ItemAssetCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AssetViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AssetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AssetViewHolder(private val binding: ItemAssetCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MarketAsset) {
            val context = binding.root.context
            binding.tvAssetName.text = item.name
            binding.tvAssetSymbol.text = item.symbol
            binding.tvAssetPrice.text = Formatters.formatPrice(item.price)
            binding.tvChangePercent.text = Formatters.formatPercent(item.changePercent)

            val isPositive = item.changePercent >= 0
            val colorRes = if (isPositive) R.color.market_positive else R.color.market_negative
            val pillBgRes = if (isPositive) R.drawable.bg_pill_positive else R.drawable.bg_pill_negative
            val iconRes = if (isPositive) R.drawable.ic_trending_up else R.drawable.ic_trending_down

            val color = ContextCompat.getColor(context, colorRes)
            binding.tvChangePercent.setTextColor(color)
            binding.layoutChangePill.setBackgroundResource(pillBgRes)
            binding.ivTrend.setImageResource(iconRes)
            binding.ivTrend.setColorFilter(color)
        }
    }

    private class AssetDiffCallback : DiffUtil.ItemCallback<MarketAsset>() {
        override fun areItemsTheSame(oldItem: MarketAsset, newItem: MarketAsset): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: MarketAsset, newItem: MarketAsset): Boolean {
            return oldItem == newItem
        }
    }
}