package com.bittv.iptv.ui

import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bittv.iptv.R
import com.bittv.iptv.data.Channel
import com.bittv.iptv.util.LogoLoader

class ChannelAdapter(
    private val onChannelClick: (Channel) -> Unit,
    private val onFavoriteClick: (Channel) -> Unit,
    private val isFavorite: (Channel) -> Boolean,
    private val isSelected: (Channel) -> Boolean = { false }
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

    private val items = mutableListOf<Channel>()
    private var lastAnimatedPosition = -1

    fun submitList(channels: List<Channel>) {
        items.clear()
        items.addAll(channels)
        lastAnimatedPosition = -1
        notifyDataSetChanged()
    }

    fun currentItems(): List<Channel> = items.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        holder.bind(items[position])
        animateEntrance(holder.itemView, position)
    }

    /** Pas row discroll keluar layar dan di-recycle, matiin animasi dulu —
     *  kalau tidak, animator infinite-nya tetap jalan terus di belakang
     *  layar biarpun rownya udah gak keliatan, boros baterai buat list
     *  yang panjang. */
    override fun onViewRecycled(holder: ChannelViewHolder) {
        super.onViewRecycled(holder)
        holder.stopLivePulse()
    }

    /** Fade + geser naik pas item pertama kali muncul di layar, biar list terasa hidup. */
    private fun animateEntrance(view: View, position: Int) {
        if (position > lastAnimatedPosition) {
            lastAnimatedPosition = position
            view.alpha = 0f
            view.translationY = 40f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((position % 10) * 30L)
                .setDuration(260)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            view.animate().cancel()
            view.alpha = 1f
            view.translationY = 0f
        }
    }

    override fun getItemCount(): Int = items.size

    inner class ChannelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val logo: ImageView = itemView.findViewById(R.id.channelLogo)
        private val initial: TextView = itemView.findViewById(R.id.channelInitial)
        private val name: TextView = itemView.findViewById(R.id.channelName)
        private val group: TextView = itemView.findViewById(R.id.channelGroup)
        private val live: TextView = itemView.findViewById(R.id.channelLive)
        private val nowPlaying: TextView = itemView.findViewById(R.id.channelNowPlaying)
        private val favorite: ImageButton = itemView.findViewById(R.id.favoriteButton)
        private var liveAnimator: ObjectAnimator? = null

        fun bind(channel: Channel) {
            name.text = channel.name
            group.text = channel.group.ifBlank { "Ungrouped" }
            live.text = "● LIVE"
            pulseLive(live)

            val selected = isSelected(channel)
            itemView.isSelected = selected
            nowPlaying.visibility = if (selected) View.VISIBLE else View.GONE

            val favorited = isFavorite(channel)
            favorite.setImageResource(
                if (favorited) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            favorite.setColorFilter(
                itemView.resources.getColor(
                    if (favorited) R.color.favorite_gold else R.color.text_secondary,
                    itemView.context.theme
                )
            )

            val logoUrl = channel.logoUrl
            if (!logoUrl.isNullOrBlank()) {
                initial.visibility = View.GONE
                logo.visibility = View.VISIBLE
                LogoLoader.load(logoUrl, logo)
            } else {
                logo.visibility = View.VISIBLE
                initial.visibility = View.VISIBLE
                initial.text = getInitial(channel)
                initial.setTextColor(Color.WHITE)
                initial.setTypeface(Typeface.DEFAULT, Typeface.BOLD)
                initial.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 18f
                    setColor(getLogoColor(channel.id))
                }
                logo.setImageResource(android.R.color.transparent)
            }

            itemView.setOnClickListener { onChannelClick(channel) }
            favorite.setOnClickListener {
                onFavoriteClick(channel)
                it.animate()
                    .scaleX(1.35f).scaleY(1.35f)
                    .setDuration(110)
                    .withEndAction {
                        it.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
                    }
                    .start()
            }
        }

        /** Denyut alpha pelan biar badge LIVE keliatan "hidup", bukan teks statis. */
        private fun pulseLive(view: TextView) {
            stopLivePulse()
            view.alpha = 1f
            liveAnimator = ObjectAnimator.ofFloat(view, "alpha", 1f, 0.45f, 1f).apply {
                duration = 1400
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        }

        /** Matiin animator yang lagi jalan (dipanggil pas rebind atau di-recycle),
         *  biar gak numpuk banyak animator infinite jalan bareng di background. */
        fun stopLivePulse() {
            liveAnimator?.cancel()
            liveAnimator = null
        }

        private fun getInitial(channel: Channel): String =
            when (channel.id.lowercase()) {
                "mnctv" -> "MNC"
                "rtv" -> "RTV"
                "rodja-tv" -> "R"
                "moji" -> "MOJI"
                "tvone" -> "TV"
                "indosiar" -> "ID"
                else -> channel.name.trim()
                    .split(Regex("\\s+"))
                    .take(2)
                    .mapNotNull { it.firstOrNull()?.toString() }
                    .joinToString("")
                    .uppercase()
                    .take(4)
            }

        private fun getLogoColor(id: String): Int =
            when (id.lowercase()) {
                "mnctv" -> Color.rgb(30, 75, 180)
                "rtv" -> Color.rgb(230, 80, 45)
                "rodja-tv" -> Color.rgb(30, 130, 85)
                "moji" -> Color.rgb(90, 60, 170)
                "tvone" -> Color.rgb(180, 45, 45)
                "indosiar" -> Color.rgb(30, 110, 180)
                else -> Color.rgb(45, 45, 58)
            }
    }
}
