package com.bittv.iptv.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bittv.iptv.R
import com.bittv.iptv.util.LogoLoader
import com.bittv.iptv.util.MusicRepository.MusicTrack

class MusicAdapter(
    private val onTrackClick: (MusicTrack) -> Unit
) : RecyclerView.Adapter<MusicAdapter.MusicViewHolder>() {

    private val items = mutableListOf<MusicTrack>()

    fun submitList(tracks: List<MusicTrack>) {
        items.clear()
        items.addAll(tracks)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_music, parent, false)
        return MusicViewHolder(view)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        holder.bind(items[position], onTrackClick)
    }

    override fun getItemCount(): Int = items.size

    class MusicViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail: ImageView = itemView.findViewById(R.id.musicThumbnail)
        private val title: TextView = itemView.findViewById(R.id.musicTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.musicSubtitle)

        fun bind(track: MusicTrack, onTrackClick: (MusicTrack) -> Unit) {
            title.text = track.title
            subtitle.text = if (track.channel.isNotBlank()) {
                "${track.channel} • ${track.durationLabel}"
            } else {
                track.durationLabel
            }
            LogoLoader.load(track.thumbnailUrl, thumbnail)
            itemView.setOnClickListener { onTrackClick(track) }
        }
    }
}
