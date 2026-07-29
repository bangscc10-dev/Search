package com.search.browser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter(
    private val entries: List<History.Entry>,
    private val onSelect: (History.Entry) -> Unit,
    private val onDelete: (History.Entry) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.hTitle)
        val url: TextView = v.findViewById(R.id.hUrl)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = entries[position]
        holder.title.text = e.title
        holder.url.text = e.url
        holder.itemView.setOnClickListener { onSelect(e) }
        holder.itemView.setOnLongClickListener { anchor ->
            val menu = PopupMenu(anchor.context, anchor)
            menu.menu.add("Delete")
            menu.setOnMenuItemClickListener {
                onDelete(e)
                true
            }
            menu.show()
            true
        }
    }

    override fun getItemCount(): Int = entries.size
}
