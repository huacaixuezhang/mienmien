package com.mienmien.android

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TurnTimelineItem(
    val turnType: String,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

class TurnTimelineAdapter : RecyclerView.Adapter<TurnTimelineAdapter.TimelineHolder>() {
    companion object {
        private const val MAX_TIMELINE_ITEMS = 300
    }

    private val allItems = mutableListOf<TurnTimelineItem>()
    private val items = mutableListOf<TurnTimelineItem>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var filter: TimelineFilter = TimelineFilter.ALL
    private val expandedKeys = mutableSetOf<String>()
    var onItemLongPressCopy: ((String) -> Unit)? = null

    fun prepend(item: TurnTimelineItem) {
        allItems.add(0, item)
        if (allItems.size > MAX_TIMELINE_ITEMS) {
            allItems.removeAt(allItems.lastIndex)
        }
        if (matchFilter(item.turnType, filter)) {
            items.add(0, item)
            if (items.size > MAX_TIMELINE_ITEMS) {
                items.removeAt(items.lastIndex)
                notifyDataSetChanged()
            } else {
                notifyItemInserted(0)
            }
        }
    }

    fun setFilter(next: TimelineFilter) {
        filter = next
        rebuildVisibleItems()
    }

    fun clearAll() {
        allItems.clear()
        items.clear()
        notifyDataSetChanged()
    }

    fun exportText(): String {
        if (items.isEmpty()) return "（时间线为空）"
        return renderLines(items)
    }

    fun currentFilter(): TimelineFilter {
        return filter
    }

    fun stats(): TimelineStats {
        var interviewer = 0
        var candidate = 0
        allItems.forEach { item ->
            if (item.turnType.startsWith("INTERVIEWER_")) interviewer++
            if (item.turnType.startsWith("CANDIDATE_")) candidate++
        }
        return TimelineStats(
            total = allItems.size,
            interviewer = interviewer,
            candidate = candidate,
            visible = items.size
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimelineHolder {
        val container = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 10
                bottomMargin = 10
                leftMargin = 16
                rightMargin = 16
            }
        }
        val title = TextView(parent.context).apply {
            textSize = 15f
            setTextColor(Color.BLACK)
        }
        val meta = TextView(parent.context).apply {
            textSize = 12f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.START
        }
        val content = TextView(parent.context).apply {
            textSize = 13f
            setTextColor(Color.DKGRAY)
        }
        container.addView(title)
        container.addView(meta)
        container.addView(content)
        return TimelineHolder(container, title, meta, content)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: TimelineHolder, position: Int) {
        val item = items[position]
        val key = "${item.createdAt}:${item.turnType}:${item.title}"
        val expanded = expandedKeys.contains(key)
        holder.title.text = item.title
        holder.meta.text = "${timeFmt.format(Date(item.createdAt))}  (点击展开/收起，长按复制)"
        holder.content.text = item.content.ifBlank { "（无附带文本）" }
        holder.content.visibility = if (expanded) TextView.VISIBLE else TextView.GONE
        holder.container.setBackgroundColor(colorByType(item.turnType))
        holder.container.setOnClickListener {
            if (expanded) {
                expandedKeys.remove(key)
            } else {
                expandedKeys.add(key)
            }
            notifyItemChanged(position)
        }
        holder.container.setOnLongClickListener {
            val c = item.content.ifBlank { "（无附带文本）" }
            val line = "[${timeFmt.format(Date(item.createdAt))}] ${item.title} - $c"
            onItemLongPressCopy?.invoke(line)
            true
        }
    }

    private fun colorByType(type: String): Int {
        return when (type) {
            "INTERVIEWER_QUESTION_START" -> Color.parseColor("#E3F2FD")
            "INTERVIEWER_QUESTION_END" -> Color.parseColor("#E8F5E9")
            "CANDIDATE_ANSWER_START" -> Color.parseColor("#FFF3E0")
            "CANDIDATE_ANSWER_END" -> Color.parseColor("#F3E5F5")
            else -> Color.parseColor("#ECEFF1")
        }
    }

    private fun matchFilter(turnType: String, filter: TimelineFilter): Boolean {
        return when (filter) {
            TimelineFilter.ALL -> true
            TimelineFilter.INTERVIEWER -> turnType.startsWith("INTERVIEWER_")
            TimelineFilter.CANDIDATE -> turnType.startsWith("CANDIDATE_")
        }
    }

    private fun rebuildVisibleItems() {
        val oldList = items.toList()
        val newList = allItems.filter { matchFilter(it.turnType, filter) }
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = oldList.size
            override fun getNewListSize(): Int = newList.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val oldItem = oldList[oldItemPosition]
                val newItem = newList[newItemPosition]
                return oldItem.createdAt == newItem.createdAt &&
                    oldItem.turnType == newItem.turnType &&
                    oldItem.title == newItem.title
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return oldList[oldItemPosition] == newList[newItemPosition]
            }
        })
        items.clear()
        items.addAll(newList)
        diff.dispatchUpdatesTo(this)
    }

    private fun renderLines(source: List<TurnTimelineItem>): String {
        val lines = mutableListOf<String>()
        source.forEach { item ->
            val t = timeFmt.format(Date(item.createdAt))
            val c = item.content.ifBlank { "（无附带文本）" }
            lines += "[$t] ${item.title} - $c"
        }
        return lines.joinToString(separator = "\n")
    }

    class TimelineHolder(
        val container: LinearLayout,
        val title: TextView,
        val meta: TextView,
        val content: TextView
    ) : RecyclerView.ViewHolder(container)
}

enum class TimelineFilter {
    ALL,
    INTERVIEWER,
    CANDIDATE
}

data class TimelineStats(
    val total: Int,
    val interviewer: Int,
    val candidate: Int,
    val visible: Int
)
