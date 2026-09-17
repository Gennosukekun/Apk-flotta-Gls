package it.gestionechiavi.mezzi

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class DamageReportActivity:AppCompatActivity(){
 private val photos=mutableListOf<File>();private var pending:File?=null;private var reportId="";private lateinit var take:Button;private lateinit var send:Button;private lateinit var photoInfo:TextView;private lateinit var msg:TextView;private lateinit var preview:ImageView
 private val token by lazy{intent.getStringExtra("device_token").orEmpty()};private val plate by lazy{intent.getStringExtra("plate").orEmpty()}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt();private fun shape(fill:Int,r:Int=16,stroke:Int?=null)=GradientDrawable().apply{setColor(fill);cornerRadius=dp(r).toFloat();stroke?.let{setStroke(dp(1),it)}}
 private val camera=registerForActivityResult(ActivityResultContracts.TakePicture()){ok->val f=pending;if(ok&&f!=null&&f.exists()&&f.length()>0&&photos.size<6){photos.add(f);preview.setImageBitmap(sample(f));photoInfo.text="Foto allegate: ${photos.size}/6";take.text=if(photos.size<6)"Scatta un'altra foto" else "Limite di 6 foto raggiunto";take.isEnabled=photos.size<6}else if(f!=null&&!photos.contains(f))f.delete();pending=null}
 private val permission=registerForActivityResult(ActivityResultContracts.RequestPermission()){if(it)launchCamera()else msg.text="Permesso fotocamera necessario"}
 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(52),dp(20),dp(28))}
  setContentView(ScrollView(this).apply{setBackgroundColor(getColor(R.color.app_background));addView(root)})
  fun tv(s:String,z:Float=16f,bold:Boolean=false)=TextView(this).apply{text=s;textSize=z;setTextColor(getColor(R.color.text_primary));if(bold)setTypeface(typeface,Typeface.BOLD);setPadding(0,dp(5),0,dp(5))}
  root.addView(tv("SEGNALAZIONE MEZZO",12f,true));root.addView(tv("Danno / anomalia",30f,true));root.addView(tv("Furgone assegnato: $plate",17f,true))
  val categories=arrayOf("Carrozzeria","Pneumatici","Luci","Spie/guasto","Interni","Pulizia","Altro")
  val spinner=Spinner(this).apply{adapter=ArrayAdapter(this@DamageReportActivity,android.R.layout.simple_spinner_dropdown_item,categories);background=shape(getColor(R.color.surface_input),14,getColor(R.color.border_input));layoutParams=LinearLayout.LayoutParams(-1,dp(54)).apply{topMargin=dp(18)}};root.addView(spinner)
  val desc=EditText(this).apply{hint="Descrivi il danno o l'anomalia";gravity=android.view.Gravity.TOP;minLines=5;setTextColor(getColor(R.color.text_primary));setHintTextColor(getColor(R.color.text_secondary));background=shape(getColor(R.color.surface_input),14,getColor(R.color.border_input));setPadding(dp(14),dp(14),dp(14),dp(14));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12)}};root.addView(desc)
  preview=ImageView(this).apply{adjustViewBounds=true;layoutParams=LinearLayout.LayoutParams(-1,dp(190)).apply{topMargin=dp(12)}};root.addView(preview)
  photoInfo=tv("Foto allegate: 0/6",14f,true);root.addView(photoInfo)
  take=Button(this).apply{text="Scatta foto (facoltativa)";isAllCaps=false;layoutParams=LinearLayout.LayoutParams(-1,dp(54)).apply{topMargin=dp(8)}};root.addView(take)
  send=Button(this).apply{text="Invia segnalazione";isAllCaps=false;setTextColor(Color.WHITE);setTypeface(typeface,Typeface.BOLD);backgroundTintList=android.content.res.ColorStateList.valueOf(getColor(R.color.primary));layoutParams=LinearLayout.LayoutParams(-1,dp(56)).apply{topMargin=dp(12)}};root.addView(send)
  msg=tv("",14f,true);root.addView(msg);take.setOnClickListener{requestCamera()}
  send.setOnClickListener{
   val description=desc.text.toString().trim()
   if(description.isBlank()){msg.text="Inserisci una descrizione";return@setOnClickListener}
   send.isEnabled=false;take.isEnabled=false
   val payload=JSONObject().put("category",spinner.selectedItem.toString()).put("description",description)
   Api.postJson("/damage-reports",payload,token){code,r->
    if(code in 200..299){reportId=JSONObject(r).optString("id");if(photos.isEmpty())done()else upload(0)}
    else runOnUiThread{msg.text=runCatching{JSONObject(r).optString("error")}.getOrNull()?.takeIf{it.isNotBlank()}?:"Errore invio ($code)";send.isEnabled=true;take.isEnabled=photos.size<6}
   }
  }
 }
 private fun requestCamera(){if(photos.size>=6)return;if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)launchCamera()else permission.launch(Manifest.permission.CAMERA)}
 private fun launchCamera(){try{val dir=File(cacheDir,"damage_photos").apply{mkdirs()};val f=File.createTempFile("danno_",".jpg",dir);pending=f;camera.launch(FileProvider.getUriForFile(this,"$packageName.fileprovider",f))}catch(e:Exception){msg.text="Impossibile aprire la fotocamera"}}
 private fun sample(f:File):Bitmap?{val o=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(f.absolutePath,o);var s=1;while(o.outWidth/s>1200||o.outHeight/s>1200)s*=2;return BitmapFactory.decodeFile(f.absolutePath,BitmapFactory.Options().apply{inSampleSize=s})}
 private fun jpeg(f:File):ByteArray{val src=sample(f)?:throw IOException("Foto non leggibile");val out=ByteArrayOutputStream();src.compress(Bitmap.CompressFormat.JPEG,80,out);src.recycle();return out.toByteArray()}
 private fun upload(i:Int){if(i>=photos.size){done();return};val bytes=try{jpeg(photos[i])}catch(e:Exception){runOnUiThread{msg.text="Errore foto ${i+1}"};return};val body=MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("report_id",reportId).addFormDataPart("photo","danno_${i+1}.jpg",bytes.toRequestBody("image/jpeg".toMediaType())).build();val req=Request.Builder().url(Api.BASE+"/damage-report-photo").header("Authorization","Bearer $token").post(body).build();Api.client.newCall(req).enqueue(object:Callback{override fun onFailure(c:Call,e:IOException){runOnUiThread{msg.text="Segnalazione salvata, errore caricamento foto ${i+1}"}};override fun onResponse(c:Call,r:Response){r.use{if(r.isSuccessful){runOnUiThread{msg.text="Caricamento foto ${i+1}/${photos.size}…"};upload(i+1)}else runOnUiThread{msg.text="Segnalazione salvata, errore foto ${i+1} (${r.code})"}}}})}
 private fun done()=runOnUiThread{msg.text="Segnalazione inviata e associata a $plate";send.visibility=android.view.View.GONE;take.visibility=android.view.View.GONE}
}
