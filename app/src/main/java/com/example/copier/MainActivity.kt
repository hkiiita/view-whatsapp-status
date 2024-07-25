package com.example.copier

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException


class MainActivity : ComponentActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private var IMAGES_DUMP_DIRECTORY = ""
        private var WHATSAPP_MEDIA_DIRECTORY = ""
    }

    private lateinit var textView: TextView


    //@RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        textView = findViewById(R.id.textView)
        textView.movementMethod = ScrollingMovementMethod()

        // Check for permissions and then copy pictures
        checkPermissions()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkPermissions() {
        if (!Environment.isExternalStorageManager()) {
            Toast.makeText(
                this,
                "Permission not granted....Asking for permission now !",
                Toast.LENGTH_LONG
            ).show()
            requestPermissions()
            //initiateCoreLogicExecution()
        } else {
            // Permission already granted
            initiateCoreLogicExecution()
        }
    }

    private fun showAlertMessageBeforeAction(title: String, message: String, onOkClicked: () -> Unit) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("OK") { dialog, which ->
            // Call the lambda function when OK button is clicked
            onOkClicked.invoke()
        }
        builder.setCancelable(false) // Prevent dismissing dialog on outside touch or back press
        builder.show()
    }


    private val dumplingDirectoryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                uri?.let {
                    IMAGES_DUMP_DIRECTORY = it.toString()
                    Toast.makeText(this, "Selected directory: $IMAGES_DUMP_DIRECTORY", Toast.LENGTH_LONG).show()
                    chooseWhatsAppMediaDirectory()
                }
            } else {
                Toast.makeText(this, "Directory selection cancelled", Toast.LENGTH_LONG).show()
            }
        }

    private fun chooseWhatsAppMediaDirectory() {
        showAlertMessageBeforeAction("Alert", "Select location where whatsapp stores status images in your phone, generally, its at `/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/.Statuses`"){
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            whatsAppMediaChooseDirectoryLauncher.launch(intent)
        }
    }

    private val whatsAppMediaChooseDirectoryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                uri?.let {
                    WHATSAPP_MEDIA_DIRECTORY = it.toString()
                    Toast.makeText(this, "Selected directory: $WHATSAPP_MEDIA_DIRECTORY", Toast.LENGTH_LONG).show()
                    queryFilesInDirectory(WHATSAPP_MEDIA_DIRECTORY)

                }
            } else {
                Toast.makeText(this, "Directory selection cancelled", Toast.LENGTH_LONG).show()
            }
        }


    private fun initiateCoreLogicExecution(){
        showAlertMessageBeforeAction("Alert", "Select location where statuses should be stored permanently on your device."){
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            dumplingDirectoryLauncher.launch(intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:" + packageName)
                startActivityForResult(intent, PERMISSION_REQUEST_CODE)
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Exception occured when getting permission manually : $e",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                startActivityForResult(intent, PERMISSION_REQUEST_CODE)
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.MANAGE_EXTERNAL_STORAGE
//                    Manifest.permission.READ_EXTERNAL_STORAGE,
//                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                PERMISSION_REQUEST_CODE
            )
        }
    }


    // Example function to query files in the specified directory
    @SuppressLint("Range", "SetTextI18n")
    private fun queryFilesInDirectory(whatsAppMediaDirectory: String) {
        val selection = "${MediaStore.Files.FileColumns.DATA} LIKE '%$whatsAppMediaDirectory%'"
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA
        )
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

        val contentResolver = contentResolver
        val cursor = contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            null,
            sortOrder
        )

        val stringBuilder = StringBuilder()

        var count = 0
        cursor?.use { cursor ->
            while (cursor.moveToNext()) {
                count++
                val displayName =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME))
                val data =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA))
                val fileId = cursor.getLong(cursor.getColumnIndex(MediaStore.Files.FileColumns._ID))

                val file = File(data)
                if (file.isDirectory) {
                    // Skip directories
                    continue
                }


                val fileInfo = "File Name: $displayName, \nFile Path: $data, \nFile ID: $fileId"
                Log.d("-------- MainActivity", fileInfo)
                stringBuilder.append(fileInfo).append("\n\n")
                // Perform operations with the file data
                val inputStream = FileInputStream(File(data))
                val outputStream = FileOutputStream(File(IMAGES_DUMP_DIRECTORY, displayName))
                val buffer = ByteArray(2048)
                var length: Int
                try {
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        outputStream.write(buffer, 0, length)
                    }

                    //TODO://Optionally delete the original file after copying
                    // contentResolver.delete(uri, null, null)

                } catch (e: IOException) {
                    Log.e("MainActivity", "Error copying file: $displayName", e)
                    Toast.makeText(this, "Exception copying file : $e", Toast.LENGTH_LONG).show()
                } finally {
                    inputStream.close()
                    outputStream.close()
                }

            }
        }
        cursor?.close()
        stringBuilder.append("Successfully wrote $count files")
        textView.text = stringBuilder.toString()
        Toast.makeText(this, "Successfully wrote $count files", Toast.LENGTH_LONG).show()

    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    // Permission granted
                    initiateCoreLogicExecution()
                } else {
                    // Permission denied
                    Toast.makeText(
                        this,
                        "Permission denied. Cannot access files.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }


}
