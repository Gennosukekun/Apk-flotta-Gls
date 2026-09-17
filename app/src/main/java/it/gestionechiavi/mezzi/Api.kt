package it.gestionechiavi.mezzi
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
object Api {
 const val BASE="https://gestione-chiavi.hatchable.site/api"
 val client=OkHttpClient()
 private fun builder(path:String,token:String?)=Request.Builder().url(BASE+path).apply{if(!token.isNullOrBlank())header("Authorization","Bearer $token")}
 fun get(path:String,token:String?=null,cb:(Int,String)->Unit){val r=builder(path,token).build();client.newCall(r).enqueue(object:Callback{override fun onFailure(c:Call,e:IOException)=cb(0,e.message?:"Errore rete");override fun onResponse(c:Call,r:Response)=cb(r.code,r.body?.string().orEmpty())})}
 fun postJson(path:String,json:JSONObject,token:String?=null,cb:(Int,String)->Unit){val b=json.toString().toRequestBody("application/json".toMediaType());val r=builder(path,token).post(b).build();client.newCall(r).enqueue(object:Callback{override fun onFailure(c:Call,e:IOException)=cb(0,e.message?:"Errore rete");override fun onResponse(c:Call,r:Response)=cb(r.code,r.body?.string().orEmpty())})}
}