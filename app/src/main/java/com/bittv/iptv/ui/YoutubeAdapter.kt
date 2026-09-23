package com.bittv.iptv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bittv.iptv.R
import com.bittv.iptv.util.LogoLoader
import com.bittv.iptv.util.YoutubeRepository.Video

class YoutubeAdapter(
    private val onVideoClick: (Video) -> Unit
) : RecyclerView.Adapter<YoutubeAdapter.VideoViewHolder>() {

    private val items = mutableListOf<Video>()

    fun submitList(videos: List<Video>) {
        items.clear()
        items.addAll(videos)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_youtube, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(items[position], onVideoClick)
    }

    override fun getItemCount(): Int = items.size

    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail: ImageView = itemView.findViewById(R.id.youtubeThumbnail)
        private val title: TextView = itemView.findViewById(R.id.youtubeTitle)
        private val subtitle: TextView = itemView.findViewById(R.id.youtubeSubtitle)

        fun bind(video: Video, onVideoClick: (Video) -> Unit) {
            title.text = video.title
            val meta = buildList {
                if (video.channelTitle.isNotBlank()) add(video.channelTitle)
                if (video.publishedAt.isNotBlank()) add(video.publishedAt.take(10))
            }
            subtitle.text = meta.joinToString(" • ")
            LogoLoader.load(video.thumbnailUrl, thumbnail)
            itemView.setOnClickListener { onVideoClick(video) }
        }
    }
}
