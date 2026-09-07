package com.cinesubz.tv.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.cinesubz.tv.R
import com.cinesubz.tv.model.Movie

class MovieAdapter(
    private val movies: List<Movie>,
    private val onMovieClick: (Movie) -> Unit
) : RecyclerView.Adapter<MovieAdapter.MovieViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_movie_card, parent, false)
        return MovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        val movie = movies[position]
        holder.bind(movie)
    }

    override fun getItemCount(): Int = movies.size

    inner class MovieViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPoster: ImageView = itemView.findViewById(R.id.ivPoster)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvYear: TextView = itemView.findViewById(R.id.tvYear)
        private val tvQuality: TextView = itemView.findViewById(R.id.tvQuality)

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
                    onMovieClick(movies[pos])
                }
            }
        }

        fun bind(movie: Movie) {
            tvTitle.text = movie.title
            tvYear.text = movie.year ?: "2026"
            tvQuality.text = movie.rating ?: "HD"

            if (!movie.poster.isNullOrEmpty()) {
                ivPoster.load(movie.poster) {
                    crossfade(true)
                    placeholder(R.color.netflix_card_bg)
                    error(R.color.netflix_card_bg)
                }
            } else {
                ivPoster.setImageResource(R.color.netflix_card_bg)
            }
        }
    }
}
