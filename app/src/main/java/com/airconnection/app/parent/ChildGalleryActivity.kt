package com.airconnection.app.parent

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airconnection.app.databinding.ActivityChildGalleryBinding
import com.airconnection.app.databinding.ItemGalleryPhotoBinding
import com.airconnection.app.network.SignalingClient

class ChildGalleryActivity : AppCompatActivity(), SignalingClient.SignalingListener {

    private lateinit var binding: ActivityChildGalleryBinding
    private var targetDeviceId = ""
    private var childName = "Child Phone"

    private val photoBase64List = mutableListOf<String>()
    private lateinit var adapter: PhotoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChildGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        targetDeviceId = intent.getStringExtra("device_id") ?: ""
        childName = intent.getStringExtra("child_name") ?: "Child Phone"

        binding.tvGalleryChildName.text = "$childName - Gallery"
        binding.btnBackGallery.setOnClickListener { finish() }

        adapter = PhotoAdapter(photoBase64List)
        binding.rvChildPhotos.layoutManager = GridLayoutManager(this, 3)
        binding.rvChildPhotos.adapter = adapter

        SignalingClient.listener = this
        fetchPhotos()

        binding.btnRefreshPhotos.setOnClickListener {
            binding.loadingGalleryPlaceholder.visibility = View.VISIBLE
            fetchPhotos()
        }
    }

    private fun fetchPhotos() {
        SignalingClient.getPhotos(targetDeviceId)
    }

    override fun onPhotosListReceived(deviceId: String, photos: List<String>) {
        if (deviceId == targetDeviceId) {
            runOnUiThread {
                binding.loadingGalleryPlaceholder.visibility = View.GONE
                photoBase64List.clear()
                photoBase64List.addAll(photos)
                adapter.notifyDataSetChanged()
            }
        }
    }

    inner class PhotoAdapter(private val items: List<String>) :
        RecyclerView.Adapter<PhotoAdapter.ViewHolder>() {

        inner class ViewHolder(val itemBinding: ItemGalleryPhotoBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemGalleryPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            try {
                val base64Str = items[position]
                val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                holder.itemBinding.ivGalleryThumbnail.setImageBitmap(bitmap)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
