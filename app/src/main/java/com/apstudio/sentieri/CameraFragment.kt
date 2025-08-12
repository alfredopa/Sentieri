package com.apstudio.sentieri

import android.Manifest
import android.app.Activity
import android.content.ContentValues
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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import com.apstudio.sentieri.db.SentieriRepo
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton

class CameraFragment : Fragment() {
    // se richiamato da poiFragment riceve l'URI della foto da visualizzare
    private val args :  CameraFragmentArgs by navArgs()
    private lateinit var ivPhoto: ImageView
    private lateinit var fabCamera: FloatingActionButton
    private var currentImageUri: Uri? = null
    private lateinit var resultLauncher: ActivityResultLauncher<Intent>
    private lateinit var viewModel: SentieriViewModel
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity().applicationContext as AppSentieri).get(SentieriViewModel::class.java)
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fabCamera = view.findViewById(R.id.fabCamera)
        ivPhoto = view.findViewById(R.id.ivImage)
        // se è passato il parametro con l'URI della foto da visualizzare allora la carica
        // altrimenti apre la fotocamera
        if (args.uriFoto != "") {
            fabCamera.visibility = View.GONE
            currentImageUri = Uri.parse(args.uriFoto)
            openFoto(ivPhoto, currentImageUri!!)
        }
        else {
            // L'applicazione ha il permesso di accedere alla fotocamera
            // Apri la fotocamera o esegui altre operazioni
            //if (checkCameraPermission(this)) {
            resultLauncher = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    //Log.d("camera", "L'URI è:$currentImageUri")
                    openFoto(ivPhoto, currentImageUri!!)
                    // salva l'URI della foto nel viewModel per il salvataggio nel db e nella lista foto da visualizzare
                    viewModel.fotoInPoiDB.add(currentImageUri!!)
                    viewModel.fotoList.add(currentImageUri!!)
                }
            }

            // apre direttamente la fotocamera
            openCamera()

            fabCamera.setOnClickListener {
                if (checkCameraPermission(requireContext())) {
                    openCamera()
                } else
                    requestCameraPermission(requireContext(), REQUEST_CAMERA_PERMISSION)
            }
        }
    }

    private fun openFoto(imageView: ImageView, uri: Uri) {
        // utilizza Glide per caricare l'immagine
        Glide.with(imageView.context)
            .load(uri)
            .into(imageView)
        val nomeFoto = MapUtils.getFileNameFromUri(requireContext(), uri)
        //Log.d("camera", "nome foto $nomeFoto")
    }

    private fun openCamera() {
        val values = ContentValues()
        values.put(MediaStore.Images.Media.TITLE, "Sentieri")
        values.put(MediaStore.Images.Media.DESCRIPTION, "foto waypoint")
        values.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Sentieri")
        currentImageUri = requireContext().contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        )
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, currentImageUri)
        resultLauncher.launch(intent)
        //Log.d(ContentValues.TAG, "L'URI è:$currentImageUri")
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