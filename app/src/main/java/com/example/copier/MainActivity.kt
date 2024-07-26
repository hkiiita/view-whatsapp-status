package com.example.copier

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class MainActivity : ComponentActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private var IMAGES_DUMP_DIRECTORY_URI: Uri? = null
        private var WHATSAPP_MEDIA_DIRECTORY_URI: Uri? = null
    }

    private lateinit var textView: TextView

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textView = findViewById(R.id.textView)
        textView.movementMethod = ScrollingMovementMethod()

        checkPermissions()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkPermissions() {
        if (!Environment.isExternalStorageManager()) {
            showAlertMessage("Info", "Permission not granted....Asking for permission now!"){}
            requestPermissions()
        } else {
            initiateCoreLogicExecution()
        }
    }

    private fun showAlertMessage(title: String, message: String, onOkClicked: () -> Unit) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("OK", ){ _, _ ->
            onOkClicked.invoke()
        }
        builder.setCancelable(false)
        builder.show()
    }

    private fun showAlertMessageBeforeAction(title: String, message: String, onOkClicked: () -> Unit) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("OK") { _, _ ->
            onOkClicked.invoke()
        }
        builder.setCancelable(false)
        builder.show()
    }

    private val dumplingDirectoryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                uri?.let {
                    IMAGES_DUMP_DIRECTORY_URI = it
                    showAlertMessage("Info", "Selected Directory: $IMAGES_DUMP_DIRECTORY_URI"){
                        chooseWhatsAppMediaDirectory()
                    }

                }
            } else {
                showAlertMessage("Info", "Directory Selection Cancelled: You cancelled the directory selection."){}
            }
        }

    private fun chooseWhatsAppMediaDirectory() {
        showAlertMessageBeforeAction("Alert", "Select location where WhatsApp stores status images in your phone, generally, it's at `/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/.Statuses`") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            whatsAppMediaChooseDirectoryLauncher.launch(intent)
        }
    }

    private val whatsAppMediaChooseDirectoryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                uri?.let {
                    WHATSAPP_MEDIA_DIRECTORY_URI = it
                    showAlertMessage("Info", "Selected Directory: $WHATSAPP_MEDIA_DIRECTORY_URI") {
                        queryFilesInDirectory(WHATSAPP_MEDIA_DIRECTORY_URI)
                    }
                }
            } else {
                showAlertMessage("Info", "Directory Selection Cancelled: You cancelled the directory selection."){}
            }
        }

    private fun initiateCoreLogicExecution() {
        showAlertMessageBeforeAction("Alert", "Select location where statuses should be stored permanently on your device.") {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            dumplingDirectoryLauncher.launch(intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, PERMISSION_REQUEST_CODE)
            } catch (e: Exception) {
                showAlertMessage("Info", "Exception occurred when getting permission manually: $e"){
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivityForResult(intent, PERMISSION_REQUEST_CODE)
                }

            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initiateCoreLogicExecution()
            } else {
                showAlertMessage("Info", "Permission denied. Cannot access files."){}
            }
        }
    }

    @SuppressLint("Range", "SetTextI18n")
    private fun queryFilesInDirectory(directoryUri: Uri?) {
        if (directoryUri == null) return

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            directoryUri,
            DocumentsContract.getTreeDocumentId(directoryUri)
        )

        val cursor = contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null,
            null,
            null
        )

        val stringBuilder = StringBuilder()
        var count = 0

        cursor?.use {
            while (it.moveToNext()) {
                val documentId = it.getString(it.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                val displayName = it.getString(it.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                val mimeType = it.getString(it.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE))

                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    continue
                }

                val documentUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, documentId)
                val fileInfo = "File Name: $displayName, \nFile ID: $documentId"
                Log.d("MainActivity", fileInfo)
                stringBuilder.append(fileInfo).append("\n\n")

                try {
                    copyFile(documentUri, IMAGES_DUMP_DIRECTORY_URI)
                    count++
                } catch (e: Exception) {
                    showAlertMessage("Info", "Exception copying file: $e"){
                        Log.e("MainActivity", "Error copying file: $displayName", e)
                    }

                }
            }
        }

        cursor?.close()
        stringBuilder.append("Successfully wrote $count files")
        textView.text = stringBuilder.toString()
        showAlertMessage("Info", "Successfully wrote $count files"){}
    }

    private fun copyFile(srcUri: Uri, destUri: Uri?) {
        if (destUri == null) {
            Log.e("MainActivity", "Destination URI is null")
            return
        }

        val displayName = getFileNameFromUri(srcUri) ?: return

        val destDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
            destUri,
            DocumentsContract.getTreeDocumentId(destUri)
        )

        val destFileUri = DocumentsContract.createDocument(
            contentResolver,
            destDocumentUri,
            "image/jpeg", // Assuming the files being copied are images, adjust the MIME type as needed
            displayName
        ) ?: return

        try {
            contentResolver.openInputStream(srcUri).use { input ->
                if (input == null) {
                    Log.e("MainActivity", "InputStream is null for URI: $srcUri")
                    return
                }
                contentResolver.openOutputStream(destFileUri).use { output ->
                    val buffer = ByteArray(1024)
                    var length: Int
                    while (input.read(buffer).also { length = it } > 0) {
                        output?.write(buffer, 0, length)
                    }
                }
            }
            Log.d("MainActivity", "Copied file: $displayName")
        } catch (e: IOException) {
            showAlertMessage("Info", "Error copying file: $displayName"){
                Log.e("MainActivity", "Error copying file: $displayName", e)
            }

        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }
}
