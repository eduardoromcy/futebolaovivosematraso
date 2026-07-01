package com.e2dsys.futebolaovivosematraso

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_main)

        val gridView = findViewById<VerticalGridView>(R.id.gridView)
        gridView.setNumColumns(3)

        lifecycleScope.launch {
            val streams = withContext(Dispatchers.IO) {
                YouTubeChannelScraper.getLiveStreams()
            }
            gridView.adapter = TvStreamAdapter(streams) { stream ->
                startActivity(
                    Intent(this@TvMainActivity, PlayerActivity::class.java).apply {
                        putExtra("video_url", stream.url)
                    }
                )
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
        private val badge: TextView = view.findViewById(R.id.liveBadge)

        fun bind(stream: LiveStreamItem) {
            title.text = stream.title
            badge.text = "⚡0 ATRASO"
        }
    }
}
