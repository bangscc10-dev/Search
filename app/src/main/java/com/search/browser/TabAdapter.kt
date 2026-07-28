package com.search.browser

import android.graphics.Matrix
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TabAdapter(
    private val tabs: List<Tab>,
    private val onSelect: (Tab) -> Unit,
    private val onClose: (Tab) -> Unit
) : RecyclerView.Adapter<TabAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.tabThumb)
        val title: TextView = v.findViewById(R.id.tabTitle)
        val close: ImageButton = v.findViewById(R.id.closeTab)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tab, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val tab = tabs[position]
        holder.title.text = tab.title.ifBlank { "New Tab" }
        holder.itemView.setOnClickListener { onSelect(tab) }
        holder.close.setOnClickListener { onClose(tab) }

        val bmp = tab.thumbnail
        if (bmp != null) {
            holder.thumb.setImageBitmap(bmp)
            // Scale the (wide) capture to fit the card width, pinned to the top.
            holder.thumb.post {
                val vw = holder.thumb.width.toFloat()
                if (vw > 0 && bmp.width > 0) {
                    val scale = vw / bmp.width.toFloat()
                    val m = Matrix()
                    m.setScale(scale, scale)
                    holder.thumb.imageMatrix = m
                }
            }
        } else {
            holder.thumb.setImageDrawable(null)
        }
    }

    override fun getItemCount(): Int = tabs.size
}
