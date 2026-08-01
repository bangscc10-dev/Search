package com.search.browser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

class SuggestAdapter(
    private var items: List<JSONObject>,
    private val onPick: (JSONObject) -> Unit
) : RecyclerView.Adapter<SuggestAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.sugIcon)
        val text: TextView = v.findViewById(R.id.sugText)
    }

    fun submit(newItems: List<JSONObject>) { items = newItems; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_suggestion, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.text.text = item.optString("title")
        holder.icon.setImageResource(when (item.optString("kind")) {
            "history" -> R.drawable.ic_sug_history
            "bookmark" -> R.drawable.ic_sug_bookmark
            else -> R.drawable.ic_sug_web
        })
        holder.itemView.setOnClickListener { onPick(item) }
    }

    override fun getItemCount(): Int = items.size
}
