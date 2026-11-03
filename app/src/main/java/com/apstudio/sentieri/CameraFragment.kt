package com.apstudio.sentieri

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File

class CameraFragment : Fragment() {
    private val args: CameraFragmentArgs by navArgs()
    private lateinit var viewPagerPhotos: ViewPager2
    private lateinit var tvPhotoCounter: TextView
    private lateinit var fabCamera: FloatingActionButton
    private var currentImageUri: Uri? = null
    private var currentPhotoFile: File? = null
    private lateinit var resultLauncher: ActivityResultLauncher<Intent>

    private val viewModel: SentieriViewModel by activityViewModels {
        val application = requireActivity().application
        val database = SentieriDB.getInstance(application)
        val repository = SentieriRepo(
            sentieriDao = database.sentieriDao(),
            trackDao = database.trackDao(),
            poiDao = database.poiDao(),
            fotoPoiDao = database.fotoPoiDao()
        )
        SentieriFactory(repository, application)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fabCamera = view.findViewById(R.id.fabCamera)
        viewPagerPhotos = view.findViewById(R.id.viewPagerPhotos)
        tvPhotoCounter = view.findViewById(R.id.tvPhotoCounter)

        if (args.uriFoto != "") {
            fabCamera.visibility = View.GONE
            val targetUri = args.uriFoto.toUri()
            currentImageUri = targetUri

            val photosList = if (viewModel.currentPhotosList.isNotEmpty()) {
                viewModel.currentPhotosList
            } else {
                listOf(targetUri)
            }

            viewPagerPhotos.adapter = FullscreenImageAdapter(photosList)

            val startIndex = if (args.initialPosition in photosList.indices) {
                args.initialPosition
            } else {
                val foundIndex = photosList.indexOfFirst {
                    MapUtils.getFileNameFromUri(requireContext(), it) == MapUtils.getFileNameFromUri(requireContext(), targetUri)
                }
                if (foundIndex != -1) foundIndex else 0
            }

            viewPagerPhotos.setCurrentItem(startIndex, false)

            if (photosList.size > 1) {
                tvPhotoCounter.visibility = View.VISIBLE
                tvPhotoCounter.text = getString(R.string.photo_counter_format, startIndex + 1, photosList.size)
                viewPagerPhotos.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        tvPhotoCounter.text = getString(R.string.photo_counter_format, position + 1, photosList.size)
                    }
                })
            } else {
                tvPhotoCounter.visibility = View.GONE
            }
        } else {
            tvPhotoCounter.visibility = View.GONE
            resultLauncher = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    currentImageUri?.let { uri ->
                        viewModel.fotoInPoiDB.add(uri)
                        viewModel.fotoList.add(uri)
                        viewPagerPhotos.adapter = FullscreenImageAdapter(listOf(uri))
                    }
                }
            }

            openCamera()

            fabCamera.setOnClickListener {
                if (checkCameraPermission(requireContext())) {
                    openCamera()
                } else {
                    requestCameraPermission(requireContext(), REQUEST_CAMERA_PERMISSION)
                }
            }
        }
    }

    private open class FullscreenImageAdapter(private val images: List<Uri>) :
        RecyclerView.Adapter<FullscreenImageAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_fullscreen_photo, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(images[position])
        }

        override fun getItemCount(): Int = images.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val imageView: ImageView = itemView.findViewById(R.id.ivFullscreenPhoto)

            fun bind(uri: Uri) {
                val mediaData = MapUtils.resolveMediaUri(itemView.context, uri.toString())
                Glide.with(itemView.context)
                    .load(mediaData)
                    .error(android.R.drawable.ic_dialog_alert)
                    .into(imageView)
            }
        }
    }

    private fun openCamera() {
        @Suppress("DEPRECATION")
        val appMediaDir = requireContext().externalMediaDirs.firstOrNull()
            ?: requireContext().getExternalFilesDir(null)
            ?: requireContext().filesDir

        val fotoDir = File(appMediaDir, "Foto").apply {
            if (!exists()) mkdirs()
        }

        val photoName = "${System.currentTimeMillis()}.jpg"
        val photoFile = File(fotoDir, photoName)
        currentPhotoFile = photoFile

        currentImageUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.provider",
            photoFile
        )

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, currentImageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        resultLauncher.launch(intent)
    }

    private fun checkCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission(context: Context, requestCode: Int) {
        ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.CAMERA), requestCode)
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 10
    }
}
