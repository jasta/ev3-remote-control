package org.devtcg.robotrc.robotselection.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.devtcg.robotrc.databinding.RobotChooserItemBinding
import org.devtcg.robotrc.robotdata.network.DiscoveredPeer
import org.devtcg.robotrc.robotselection.model.RobotTarget

internal class DiscoveredPeerAdapter(
  private val fragment: BottomSheetDialogFragment,
  private val selectionTarget: MutableLiveData<RobotTarget?>,
): ListAdapter<DiscoveredPeer, DiscoveredPeerAdapter.RobotViewHolder>(DiscoveredPeerDiffCallback()) {
  init {
    selectionTarget.observe(fragment) {
      notifyDataSetChanged()
    }
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RobotViewHolder {
    val holder = RobotViewHolder(
      RobotChooserItemBinding.inflate(
        LayoutInflater.from(parent.context),
        parent,
        false))
    holder.binding.root.setOnClickListener {
      onItemClicked(getItem(holder.bindingAdapterPosition))
    }
    holder.binding.disconnect.setOnClickListener {
      onDisconnectClicked(getItem(holder.bindingAdapterPosition))
    }
    return holder
  }

  override fun onBindViewHolder(holder: RobotViewHolder, position: Int) {
    val item = getItem(position)
    holder.binding.robotName.text = item.targetName
    val isSelected = item.targetName == selectionTarget.value?.displayName
    holder.binding.disconnect.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
  }

  private fun onItemClicked(item: DiscoveredPeer) {
    // TODO: Do not hardcode supported layouts!
    selectionTarget.value = RobotTarget(
      item.targetName,
      item.targetSocketAddr,
      supportedLayouts = listOf("ev3"))
    fragment.dismiss()
  }

  private fun onDisconnectClicked(item: DiscoveredPeer) {
    if (selectionTarget.value?.displayName == item.targetName) {
      selectionTarget.value = null
    }
  }

  inner class RobotViewHolder(
    val binding: RobotChooserItemBinding
  ): RecyclerView.ViewHolder(binding.root)

  internal class DiscoveredPeerDiffCallback: DiffUtil.ItemCallback<DiscoveredPeer>() {
    override fun areItemsTheSame(oldItem: DiscoveredPeer, newItem: DiscoveredPeer): Boolean {
      return oldItem.targetName == newItem.targetName
    }

    override fun areContentsTheSame(
      oldItem: DiscoveredPeer,
      newItem: DiscoveredPeer
    ): Boolean {
      return oldItem.targetName == newItem.targetName &&
          oldItem.targetSocketAddr == oldItem.targetSocketAddr
    }
  }
}
