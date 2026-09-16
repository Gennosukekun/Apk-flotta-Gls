package it.gestionechiavi.mezzi
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
object Api { const val BASE="https://gestione-chiavi.hatchable.site/api"; val client=OkHttpClient(); fun get(path:String,cb:(Int,String)->Unit){val r=Request.Builder().url(BASE+path).build();client.newCall(r).enqueue(object:Callback{override fun onFailure(c:Call,e:IOException)=cb(0,e.message?:"Errore rete");override fun onResponse(c:Call,r:Response)=cb(r.code,r.body?.string().orEmpty())})}; fun postJson(path:String,json:JSONObject,cb:(Int,String)->Unit){val b=json.toString().toRequestBody("application/json".toMediaType());val r=Request.Builder().url(BASE+path).post(b).build();client.newCall(r).enqueue(object:Callback{override fun onFailure(c:Call,e:IOException)=cb(0,e.message?:"Errore rete");override fun onResponse(c:Call,r:Response)=cb(r.code,r.body?.string().orEmpty())})}}
