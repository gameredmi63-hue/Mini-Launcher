package com.example.minilauncher

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView

data class AppEntry(
    val label: String,
    val pkg: String,
    val component: android.content.ComponentName,
    val icon: Drawable,
    val letter: String
)

private const val TYPE_HEADER = 0
private const val TYPE_APP = 1

class AppAdapter(
    private val onClick: (View, AppEntry) -> Unit,
    private val onLongClick: (View, AppEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var apps: List<AppEntry> = emptyList()

    fun submit(list: List<AppEntry>) {
        apps = list
        notifyDataSetChanged()
    }

    /** Adapter position of the first app starting with [letter] (0 is the clock header). */
    fun positionOfLetter(letter: String): Int {
        val i = apps.indexOfFirst { it.letter == letter }
        return if (i < 0) 0 else i + 1
    }

    override fun getItemCount() = apps.size + 1

    override fun getItemViewType(position: Int) = if (position == 0) TYPE_HEADER else TYPE_APP

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            object : RecyclerView.ViewHolder(inflater.inflate(R.layout.item_header, parent, false)) {}
        } else {
            AppHolder(inflater.inflate(R.layout.item_app, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AppHolder) holder.bind(apps[position - 1])
    }

    inner class AppHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val icon: ImageView = v.findViewById(R.id.icon)
        private val label: TextView = v.findViewById(R.id.label)

        init {
            // Springy press feedback; returning false keeps click/long-click working.
            v.setOnTouchListener { view, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        view.pivotX = 28f * view.resources.displayMetrics.density
                        view.pivotY = view.height / 2f
                        springTo(view, 0.94f)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> springTo(view, 1f)
                }
                false
            }
        }

        fun bind(e: AppEntry) {
            icon.setImageDrawable(e.icon)
            label.text = e.label
            itemView.setOnClickListener { onClick(itemView, e) }
            itemView.setOnLongClickListener {
                onLongClick(itemView, e)
                true
            }
        }
    }

    private fun springTo(v: View, target: Float) {
        listOf(DynamicAnimation.SCALE_X, DynamicAnimation.SCALE_Y).forEach { prop ->
            SpringAnimation(v, prop).apply {
                spring = SpringForce(target).apply {
                    stiffness = SpringForce.STIFFNESS_MEDIUM
                    dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                }
                start()
            }
        }
    }
}
