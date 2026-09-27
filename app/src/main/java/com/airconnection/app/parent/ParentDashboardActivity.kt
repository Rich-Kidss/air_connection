package com.airconnection.app.parent

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airconnection.app.databinding.ActivityParentDashboardBinding
import com.airconnection.app.databinding.ItemChildDeviceBinding
import com.airconnection.app.models.ChildDevice
import com.airconnection.app.network.SignalingClient

class ParentDashboardActivity : AppCompatActivity(), SignalingClient.SignalingListener {

    private lateinit var binding: ActivityParentDashboardBinding
    private var parentEmail = "parent@gmail.com"
    private val deviceList = mutableListOf<ChildDevice>()
    private lateinit var adapter: ChildDeviceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParentDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        parentEmail = intent.getStringExtra("parent_email") ?: "parent@gmail.com"
        binding.tvParentAccountEmail.text = "Logged in: $parentEmail"

        adapter = ChildDeviceAdapter(deviceList)
        binding.rvChildDevices.layoutManager = LinearLayoutManager(this)
        binding.rvChildDevices.adapter = adapter

        SignalingClient.init(this)
        SignalingClient.listener = this
        SignalingClient.connect()
        SignalingClient.registerParent(parentEmail)

        binding.btnAddChild.setOnClickListener {
            val intent = Intent(this, ChildPairingActivity::class.java).apply {
                putExtra("parent_email", parentEmail)
            }
            startActivity(intent)
        }

        // Realtime child list sync from Supabase
        binding.emptyStateLayout.visibility = if (deviceList.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDeviceListUpdated(devices: List<ChildDevice>) {
        runOnUiThread {
            deviceList.clear()
            deviceList.addAll(devices)
            adapter.notifyDataSetChanged()
            binding.emptyStateLayout.visibility = if (deviceList.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    inner class ChildDeviceAdapter(private val items: List<ChildDevice>) :
        RecyclerView.Adapter<ChildDeviceAdapter.ViewHolder>() {

        inner class ViewHolder(val itemBinding: ItemChildDeviceBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemChildDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val dev = items[position]
            holder.itemBinding.tvCustomName.text = dev.customName
            holder.itemBinding.tvModel.text = "${dev.model} • 🔋 ${dev.batteryLevel}%"
            holder.itemBinding.tvStatusBadge.text = dev.status.uppercase()

            // Custom Name / Nickname Editor Dialog
            holder.itemBinding.btnRename.setOnClickListener {
                showRenameDialog(dev)
            }

            // Live Mirror Button
            holder.itemBinding.btnLiveMirror.setOnClickListener {
                val intent = Intent(this@ParentDashboardActivity, LiveScreenMirrorActivity::class.java).apply {
                    putExtra("device_id", dev.deviceId)
                    putExtra("child_name", dev.customName)
                    putExtra("parent_email", parentEmail)
                }
                startActivity(intent)
            }

            // Remote Camera Button
            holder.itemBinding.btnLiveCamera.setOnClickListener {
                val intent = Intent(this@ParentDashboardActivity, LiveCameraActivity::class.java).apply {
                    putExtra("device_id", dev.deviceId)
                    putExtra("child_name", dev.customName)
                    putExtra("parent_email", parentEmail)
                }
                startActivity(intent)
            }

            // Remote Gallery Button
            holder.itemBinding.btnChildGallery.setOnClickListener {
                val intent = Intent(this@ParentDashboardActivity, ChildGalleryActivity::class.java).apply {
                    putExtra("device_id", dev.deviceId)
                    putExtra("child_name", dev.customName)
                    putExtra("parent_email", parentEmail)
                }
                startActivity(intent)
            }

            // Anti-Uninstall Lock Toggle
            holder.itemBinding.btnUninstallLock.setOnClickListener {
                val nextState = !dev.canUninstall
                SignalingClient.setUninstallLock(dev.deviceId, nextState)
                val msg = if (nextState) "Uninstall permission granted to child device." else "Anti-uninstall protection locked."
                Toast.makeText(this@ParentDashboardActivity, msg, Toast.LENGTH_SHORT).show()
            }

            // Remote Lock Button
            holder.itemBinding.btnRemoteLock.setOnClickListener {
                SignalingClient.lockDevice(dev.deviceId, true)
                Toast.makeText(this@ParentDashboardActivity, "Remote screen lock signal sent to ${dev.customName}", Toast.LENGTH_SHORT).show()
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private fun showRenameDialog(device: ChildDevice) {
        val input = EditText(this).apply {
            setText(device.customName)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Set Custom Nickname")
            .setMessage("Give this child device a friendly name (e.g. Rahul's Phone, Sara's Tab):")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    device.customName = newName
                    adapter.notifyDataSetChanged()
                    SignalingClient.renameChild(device.deviceId, newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
