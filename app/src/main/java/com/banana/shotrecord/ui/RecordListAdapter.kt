package com.banana.shotrecord.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.banana.shotrecord.R
import com.banana.shotrecord.data.Record
import com.banana.shotrecord.databinding.ItemRecordBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

class RecordListAdapter(
    private val onPlayClick: (Record) -> Unit,
    private val onDeleteClick: (Record) -> Unit,
) : RecyclerView.Adapter<RecordListAdapter.ViewHolder>() {

    private var items: List<Record> = emptyList()
    private var playingId: Long? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    class ViewHolder(val binding: ItemRecordBinding) : RecyclerView.ViewHolder(binding.root)

    fun submit(items: List<Record>) {
        this.items = items
        notifyDataSetChanged()
    }

    fun setPlayingId(id: Long?) {
        playingId = id
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = items[position]
        val b = holder.binding
        val context = b.root.context
        b.titleText.text = dateFormat.format(Date(record.createdAt))
        b.subText.text = context.getString(R.string.record_duration, formatDuration(record.durationMs))
        b.playButton.setImageResource(
            if (playingId == record.id) R.drawable.ic_pause else R.drawable.ic_play
        )
        b.playButton.contentDescription =
            context.getString(if (playingId == record.id) R.string.pause else R.string.play)
        b.playButton.setOnClickListener { onPlayClick(record) }
        b.deleteButton.setOnClickListener { onDeleteClick(record) }
    }

    override fun getItemCount() = items.size
}
