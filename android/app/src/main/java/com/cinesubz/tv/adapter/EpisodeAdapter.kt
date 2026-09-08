package com.cinesubz.tv.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cinesubz.tv.R
import com.cinesubz.tv.model.Episode

class EpisodeAdapter(
    private var episodes: List<Episode>,
    private val onEpisodeClick: (Episode) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder>() {

    fun updateEpisodes(newEpisodes: List<Episode>) {
        this.episodes = newEpisodes
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_episode, parent, false)
        return EpisodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val episode = episodes[position]
        holder.bind(episode)
    }

    override fun getItemCount(): Int = episodes.size

    inner class EpisodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivThumb: ImageView = itemView.findViewById(R.id.ivEpisodeThumb)
        private val tvBadge: TextView = itemView.findViewById(R.id.tvEpisodeBadge)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvEpisodeTitle)
        private val tvDate: TextView = itemView.findViewById(R.id.tvEpisodeDate)

        init {
            // Android TV D-Pad Focus Animation
            itemView.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    view.animate()
                        .scaleX(1.08f)
                        .scaleY(1.08f)
                        .translationZ(8f)
                        .setDuration(180)
                        .start()
                } else {
                    view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationZ(0f)
                        .setDuration(180)
                        .start()
                }
            }

            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onEpisodeClick(episodes[pos])
                }
            }
        }

        fun bind(episode: Episode) {
            tvBadge.text = "EP ${episode.episodeNumber}"
            tvTitle.text = episode.title
            tvDate.text = episode.date ?: ""

            if (!episode.thumbnail.isNullOrEmpty()) {
                ivThumb.load(episode.thumbnail) {
                    crossfade(true)
                    placeholder(R.color.netflix_card_bg)
                    error(R.color.netflix_card_bg)
                }
            } else {
                ivThumb.setImageResource(R.color.netflix_card_bg)
            }
        }
    }
}
