package com.pansou.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

/** 搜索结果列表适配器 */
class ResultAdapter(
    private val onClick: (CloudItem) -> Unit
) : RecyclerView.Adapter<ResultAdapter.Holder>() {

    private val items = mutableListOf<CloudItem>()

    fun submit(newItems: List<CloudItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    /** 当前显示的结果（供批量检测用） */
    fun currentItems(): List<CloudItem> = items.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_result, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view as MaterialCardView
        private val title: TextView = view.findViewById(R.id.itemTitle)
        private val type: TextView = view.findViewById(R.id.itemType)
        private val valid: TextView = view.findViewById(R.id.itemValid)
        private val source: TextView = view.findViewById(R.id.itemSource)
        private val time: TextView = view.findViewById(R.id.itemTime)
        private val password: TextView = view.findViewById(R.id.itemPassword)

        fun bind(item: CloudItem) {
            title.text = item.title
            type.text = Api.typeLabel(item.type)
            valid.visibility = if (item.valid) View.VISIBLE else View.GONE
            source.text = item.source
            time.text = Api.formatTime(item.datetime)
            if (item.password.isNotBlank()) {
                password.visibility = View.VISIBLE
                password.text = itemView.context.getString(R.string.password_format, item.password)
            } else {
                password.visibility = View.GONE
            }
            card.setOnClickListener { onClick(item) }
        }
    }
}
