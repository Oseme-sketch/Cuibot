package com.example.cuibot.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cuibot.R
import com.example.cuibot.databinding.ChatItemBinding
import com.example.cuibot.model.ChatMessage

/**
 * Adapter for [RecyclerView] in MainActivity. Displays [ChatMessage] data objects.
 */
class ChatAdapter(
    private val onItemClicked: (position: Int, target: Int) -> Unit
) : ListAdapter<ChatMessage, ChatAdapter.ChatViewHolder>(DiffCallback) {

    /**
     * Create new views (invoked by the layout manager)
     */
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ChatViewHolder {
        return ChatViewHolder(
            ChatItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            ),
            onItemClicked
        )
    }

    /**
     * Get the contents of a view (invoked by the layout manager)
     */
    override fun onBindViewHolder(
        holder: ChatViewHolder,
        position: Int
    ) {
        val message = getItem(position)
        holder.bind(message)
    }

    /**
     * Provide a reference to views for the features of [ChatMessage]
     */
    class ChatViewHolder(
        private var binding: ChatItemBinding,
        private val onItemClicked: (position: Int, target: Int) -> Unit
    ): RecyclerView.ViewHolder(binding.root), View.OnClickListener {
        fun bind(chatMessage: ChatMessage) {
            when (chatMessage.receivedFrom) {
                0 -> {
                    binding.botMessage.visibility = View.GONE
                    binding.userMessage.visibility = View.VISIBLE
                    binding.richMessage.visibility = View.GONE
                    binding.userMessage.text = chatMessage.message
                }
                1 -> {
                    binding.botMessage.visibility = View.VISIBLE
                    binding.userMessage.visibility = View.GONE
                    binding.richMessage.visibility = View.GONE
                    binding.botMessage.text = chatMessage.message
                }
                else -> {
                    binding.botMessage.visibility = View.GONE
                    binding.userMessage.visibility = View.GONE
                    binding.richMessage.visibility = View.VISIBLE
                    binding.responseText.text = chatMessage.message
                    binding.leftCardButton.text = chatMessage.actions[0]
                    binding.rightCardButton.text = chatMessage.actions[2]
                    if ((chatMessage.link).isBlank()) {
                        binding.urlButton.visibility = View.GONE
                    } else {
                        binding.urlButton.visibility = View.VISIBLE
                    }
                }
            }
        }

        init {
            binding.leftCardButton.setOnClickListener(this)
            binding.rightCardButton.setOnClickListener(this)
            binding.urlButton.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
            if (v != null) {
                when(v.id) {
                    R.id.left_card_button -> {
                        val position = adapterPosition
                        val target = 0
                        onItemClicked(position, target)
                    }
                    R.id.right_card_button -> {
                        val position = adapterPosition
                        val target = 2
                        onItemClicked(position, target)
                    }
                    R.id.url_button -> {
                        val position = adapterPosition
                        val target = 10
                        onItemClicked(position, target)
                    }
                    else -> {}
                }
            }
        }
    }

    companion object DiffCallback: DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return ((oldItem.message == newItem.message)
                    && (oldItem.receivedFrom == newItem.receivedFrom))
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.message == newItem.message
        }
    }
}
