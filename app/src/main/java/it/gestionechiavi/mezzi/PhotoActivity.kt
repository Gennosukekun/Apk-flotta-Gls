package it.gestionechiavi.mezzi

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException

class PhotoActivity : AppCompatActivity() {
    private val photos = mutableListOf<Bitmap>()
    private lateinit var preview: ImageView
    private lateinit var upload: Button
    private lateinit var count: TextView
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { b ->
        if (b != null && photos.size < 6) {
            photos.add(b)
            preview.setImageBitmap(b)
            upload.isEnabled = true
            count.text = "Foto ${photos.size}/6"
            findViewById<Button>(R.id.take).text = if (photos.size < 6) "Aggiungi altra foto" else "Limite di 6 foto raggiunto"
            findViewById<Button>(R.id.take).isEnabled = photos.size < 6
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_photo)
        preview = findViewById(R.id.preview)
        upload = findViewById(R.id.upload)
        count = findViewById(R.id.photoCount)
        findViewById<Button>(R.id.take).setOnClickListener { camera.launch(null) }
        upload.setOnClickListener { uploadAll() }
    }

    private fun jpeg(src: Bitmap): ByteArray {
        val scale = minOf(1f, 1600f / maxOf(src.width, src.height))
        val b = if (scale < 1) Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true) else src
        val out = ByteArrayOutputStream(); var q = 78
        b.compress(Bitmap.CompressFormat.JPEG, q, out)
        while (out.size() > 900 * 1024 && q > 50) { q -= 8; out.reset(); b.compress(Bitmap.CompressFormat.JPEG, q, out) }
        return out.toByteArray()
    }

    private fun uploadAll() {
        if (photos.isEmpty()) return
        upload.isEnabled = false
        show("Caricamento 0/${photos.size}…")
        uploadOne(0)
    }

    private fun uploadOne(index: Int) {
        if (index >= photos.size) { show("${photos.size} foto salvate"); upload.isEnabled = true; return }
        val token = intent.getStringExtra("device_token").orEmpty().ifBlank { getSharedPreferences("device", MODE_PRIVATE).getString("device_token", "").orEmpty() }
        if (token.isBlank()) { show("Dispositivo non associato: autenticazione mancante"); upload.isEnabled = true; return }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("assignment_id", intent.getStringExtra("assignment_id").orEmpty())
            .addFormDataPart("driver_code", intent.getIntExtra("driver_code", 0).toString())
            .addFormDataPart("note", findViewById<EditText>(R.id.note).text.toString())
            .addFormDataPart("photo", "mezzo_${index + 1}.jpg", jpeg(photos[index]).toRequestBody("image/jpeg".toMediaType())).build()
        val request = Request.Builder().url(Api.BASE + "/vehicle-photo").header("Authorization", "Bearer $token").post(body).build()
        Api.client.newCall(request).enqueue(object : Callback {
            override fun onFailure(c: Call, e: IOException) { show("Errore rete sulla foto ${index + 1}"); runOnUiThread { upload.isEnabled = true } }
            override fun onResponse(c: Call, r: Response) {
                r.use {
                    if (r.isSuccessful) { show("Caricamento ${index + 1}/${photos.size}…"); uploadOne(index + 1) }
                    else { show("Errore caricamento foto ${index + 1}: ${r.code}"); runOnUiThread { upload.isEnabled = true } }
                }
            }
        })
    }

    private fun show(s: String) = runOnUiThread { findViewById<TextView>(R.id.msg).text = s }
}
