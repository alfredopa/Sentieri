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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.apstudio.sentieri.db.SentieriDB
import com.apstudio.sentieri.db.SentieriRepo
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File

class CameraFragment : Fragment() {
    // se richiamato da poiFragment riceve l'URI della foto da visualizzare
    private val args: CameraFragmentArgs by navArgs()
    private lateinit var ivPhoto: ImageView
    private lateinit var fabCamera: FloatingActionButton
    private var currentImageUri: Uri? = null
    private var currentPhotoFile: File? = null
    private lateinit var resultLauncher: ActivityResultLauncher<Intent>
    private val viewModel: SentieriViewModel by activityViewModels {
        val application = requireActivity().application
        // 1. Ottieni una singola istanza del database
        val database = SentieriDB.getInstance(application)
        // 2. Crea il repository passando TUTTI i DAO richiesti
        val repository = SentieriRepo(
            sentieriDao = database.sentieriDao(),
            trackDao = database.trackDao(),
            poiDao = database.poiDao(),
            fotoPoiDao = database.fotoPoiDao()
        )
        // 3. Crea la factory con il repository e l'applicazione
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
        ivPhoto = view.findViewById(R.id.ivImage)

        if (args.uriFoto != "") {
            fabCamera.visibility = View.GONE
            openFoto(ivPhoto, args.uriFoto)
            currentImageUri = args.uriFoto.toUri()
        } else {
            resultLauncher = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    currentImageUri?.let { uri ->
                        openFoto(ivPhoto, uri)
                        viewModel.fotoInPoiDB.add(uri)
                        viewModel.fotoList.add(uri)
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

    private fun openFoto(imageView: ImageView, data: Any) {
        val mediaData = MapUtils.resolveMediaUri(imageView.context, data.toString())
        Glide.with(imageView.context)
            .load(mediaData)
            .error(android.R.drawable.ic_dialog_alert)
            .into(imageView)
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
