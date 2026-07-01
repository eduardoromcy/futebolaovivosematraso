package com.e2dsys.futebolaovivosematraso

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.leanback.widget.VerticalGridView
import androidx.recyclerview.widget.RecyclerView
import com.e2dsys.futebolaovivosematraso.youtube.LiveStreamItem
import com.e2dsys.futebolaovivosematraso.youtube.YouTubeChannelScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TvMainActivity : AppCompatActivity() {

    private lateinit var gridView: VerticalGridView
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var retryButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_main)

        gridView = findViewById(R.id.gridView)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        errorText = findViewById(R.id.errorText)
        retryButton = findViewById(R.id.retryButton)

        gridView.setNumColumns(3)

        retryButton.setOnClickListener { loadStreams() }

        loadStreams()
    }

    private fun loadStreams() {
        gridView.visibility = View.GONE
        errorText.visibility = View.GONE
        retryButton.visibility = View.GONE
        loadingIndicator.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val streams = withContext(Dispatchers.IO) {
                    YouTubeChannelScraper.getLiveStreams()
                }
                loadingIndicator.visibility = View.GONE

                if (streams.isEmpty()) {
                    errorText.text = "Nenhuma live no momento"
                    errorText.visibility = View.VISIBLE
                    retryButton.visibility = View.VISIBLE
                } else {
                    gridView.adapter = TvStreamAdapter(streams) { stream ->
                        startActivity(
                            Intent(this@TvMainActivity, PlayerActivity::class.java).apply {
                                putExtra("video_url", stream.url)
                            }
                        )
                    }
                    gridView.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                loadingIndicator.visibility = View.GONE
                errorText.text = "Erro ao carregar: ${e.localizedMessage ?: "desconhecido"}"
                errorText.visibility = View.VISIBLE
                retryButton.visibility = View.VISIBLE
            }
        }
    }
}

class TvStreamAdapter(
    private val streams: List<LiveStreamItem>,
    private val onClick: (LiveStreamItem) -> Unit
) : RecyclerView.Adapter<TvStreamAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tv_stream, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stream = streams[position]
        holder.bind(stream)
        holder.itemView.setOnClickListener { onClick(stream) }
    }

    override fun getItemCount() = streams.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.streamTitle)
        private val viewCount: TextView = view.findViewById(R.id.viewCount)
        private val badge: TextView = view.findViewById(R.id.liveBadge)

        fun bind(stream: LiveStreamItem) {
            title.text = stream.title
            if (stream.viewCount > 0) {
                viewCount.text = "🔴 ${formatCount(stream.viewCount)} assistindo"
                viewCount.visibility = View.VISIBLE
            } else {
                viewCount.visibility = View.GONE
            }
            badge.text = "⚡0 ATRASO"
        }
    }
}
