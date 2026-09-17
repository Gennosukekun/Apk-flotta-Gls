package it.gestionechiavi.mezzi

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class PhotoActivity : AppCompatActivity() {
    private val photos = mutableListOf<File>()
    private var pendingFile: File? = null
    private lateinit var preview: ImageView
    private lateinit var upload: Button
    private lateinit var count: TextView
    private lateinit var take: Button
    private var uploadToken = ""
    private var uploadAssignment = ""
    private var uploadNote = ""
    private val initialHabitual by lazy { intent.getBooleanExtra("initial_habitual", false) }
    private val plate by lazy { intent.getStringExtra("plate").orEmpty() }

    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingFile
        if (ok && file != null && file.exists() && file.length() > 0 && photos.size < 6) {
            photos.add(file); showPreview(file); upload.isEnabled = true
            count.text = "Foto ${photos.size}/6"
            take.text = if (photos.size < 6) "Aggiungi altra foto" else "Limite di 6 foto raggiunto"
            take.isEnabled = photos.size < 6
        } else if (file != null && !photos.contains(file)) file.delete()
        pendingFile = null
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else show("Permesso fotocamera necessario per scattare le foto")
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); setContentView(R.layout.activity_photo)
        preview=findViewById(R.id.preview); upload=findViewById(R.id.upload); count=findViewById(R.id.photoCount); take=findViewById(R.id.take)
        b?.getStringArrayList("photo_paths")?.map(::File)?.filter{it.exists()&&it.length()>0}?.take(6)?.let{photos.addAll(it)}
        pendingFile=b?.getString("pending_path")?.let(::File)?.takeIf{it.exists()}
        if(photos.isNotEmpty()){showPreview(photos.last());upload.isEnabled=true;count.text="Foto ${photos.size}/6";take.text=if(photos.size<6)"Aggiungi altra foto" else "Limite di 6 foto raggiunto";take.isEnabled=photos.size<6}
        if(initialHabitual){findViewById<TextView>(R.id.msg).text="Prima documentazione del furgone abituale $plate. Scatta le foto dello stato del mezzo.";upload.text="Salva foto iniziali"}
        take.setOnClickListener { requestCamera() }; upload.setOnClickListener { uploadAll() }
    }

    override fun onSaveInstanceState(outState:Bundle){super.onSaveInstanceState(outState);outState.putStringArrayList("photo_paths",ArrayList(photos.map{it.absolutePath}));outState.putString("pending_path",pendingFile?.absolutePath)}
    private fun requestCamera(){if(photos.size>=6)return;if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)launchCamera() else cameraPermission.launch(Manifest.permission.CAMERA)}
    private fun launchCamera(){try{val dir=File(cacheDir,"vehicle_photos").apply{mkdirs()};val file=File.createTempFile("mezzo_${System.currentTimeMillis()}_",".jpg",dir);pendingFile=file;val uri:Uri=FileProvider.getUriForFile(this,"$packageName.fileprovider",file);camera.launch(uri)}catch(e:Exception){pendingFile=null;show("Impossibile aprire la fotocamera")}}
    private fun showPreview(file:File){val opts=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.absolutePath,opts);var sample=1;while(opts.outWidth/sample>1200||opts.outHeight/sample>1200)sample*=2;preview.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath,BitmapFactory.Options().apply{inSampleSize=sample}))}
    private fun jpeg(file:File):ByteArray{val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.absolutePath,bounds);var sample=1;while(bounds.outWidth/sample>2000||bounds.outHeight/sample>2000)sample*=2;val src=BitmapFactory.decodeFile(file.absolutePath,BitmapFactory.Options().apply{inSampleSize=sample})?:throw IOException("Impossibile leggere la foto");val scale=minOf(1f,1600f/maxOf(src.width,src.height));val bitmap=if(scale<1f)Bitmap.createScaledBitmap(src,(src.width*scale).toInt(),(src.height*scale).toInt(),true)else src;val out=ByteArrayOutputStream();var quality=82;bitmap.compress(Bitmap.CompressFormat.JPEG,quality,out);while(out.size()>1200*1024&&quality>50){quality-=8;out.reset();bitmap.compress(Bitmap.CompressFormat.JPEG,quality,out)};if(bitmap!==src)bitmap.recycle();src.recycle();return out.toByteArray()}
    private fun uploadAll(){if(photos.isEmpty())return;uploadToken=intent.getStringExtra("device_token").orEmpty().ifBlank{getSharedPreferences("device",MODE_PRIVATE).getString("device_token","").orEmpty()};uploadAssignment=intent.getStringExtra("assignment_id").orEmpty();uploadNote=findViewById<EditText>(R.id.note).text.toString();if(uploadToken.isBlank()){show("Dispositivo non associato: autenticazione mancante");return};upload.isEnabled=false;take.isEnabled=false;show("Caricamento 0/${photos.size}…");uploadOne(0)}
    private fun uploadOne(index:Int){if(index>=photos.size){runOnUiThread{show("${photos.size} foto salvate");upload.isEnabled=true;take.isEnabled=photos.size<6};return};val bytes=try{jpeg(photos[index])}catch(e:Exception){runOnUiThread{show("Errore lettura foto ${index+1}");upload.isEnabled=true;take.isEnabled=photos.size<6};return};val builder=MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("assignment_id",uploadAssignment).addFormDataPart("driver_code",intent.getIntExtra("driver_code",0).toString()).addFormDataPart("note",uploadNote);if(initialHabitual){builder.addFormDataPart("photo_type","abituale_iniziale").addFormDataPart("plate",plate)};val body=builder.addFormDataPart("photo","mezzo_${index+1}.jpg",bytes.toRequestBody("image/jpeg".toMediaType())).build();val request=Request.Builder().url(Api.BASE+"/vehicle-photo").header("Authorization","Bearer $uploadToken").post(body).build();Api.client.newCall(request).enqueue(object:Callback{override fun onFailure(c:Call,e:IOException){runOnUiThread{show("Errore rete sulla foto ${index+1}");upload.isEnabled=true;take.isEnabled=photos.size<6}};override fun onResponse(c:Call,r:Response){r.use{if(r.isSuccessful){show("Caricamento ${index+1}/${photos.size}…");uploadOne(index+1)}else runOnUiThread{show("Errore caricamento foto ${index+1}: ${r.code}");upload.isEnabled=true;take.isEnabled=photos.size<6}}}})}
    private fun show(s:String){if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())findViewById<TextView>(R.id.msg).text=s else runOnUiThread{findViewById<TextView>(R.id.msg).text=s}}
}
