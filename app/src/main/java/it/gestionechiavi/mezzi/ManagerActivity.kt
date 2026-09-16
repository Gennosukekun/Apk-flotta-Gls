package it.gestionechiavi.mezzi

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

class ManagerActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private var manager = ""
    private var habitualDrivers = JSONArray()
    private var habitualVehicles = JSONArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 80, 40, 40)
            setBackgroundColor(android.graphics.Color.rgb(243,246,250))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        showLogin()
    }

    private fun title(text:String, size:Float=24f)=TextView(this).apply { this.text=text; textSize=size; setTextColor(android.graphics.Color.rgb(23,32,51)); setPadding(0,12,0,12) }
    private fun input(hint:String, numeric:Boolean=false)=EditText(this).apply { this.hint=hint; setPadding(24,12,24,12); if(numeric) inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
    private fun button(text:String, action:()->Unit)=Button(this).apply { this.text=text; isAllCaps=false; setOnClickListener{action()} }
    private fun msg(text:String)=TextView(this).apply { this.text=text; setPadding(0,10,0,14) }

    private fun showLogin(){
        root.removeAllViews()
        root.addView(title("GESTIONE FLOTTA",12f)); root.addView(title("Area Responsabile",30f))
        root.addView(msg("Usa il tuo nome e PIN personale."))
        val name=input("Nome responsabile")
        val pin=input("PIN",true)
        val feedback=msg("")
        root.addView(name); root.addView(pin)
        root.addView(button("Accedi"){
            val n=name.text.toString().trim(); val p=pin.text.toString().trim()
            if(n.isBlank()||p.isBlank()){feedback.text="Inserisci nome e PIN";return@button}
            Api.postJson("/manager-login",JSONObject().put("name",n).put("pin",p)){c,s->runOnUiThread{
                if(c in 200..299){manager=runCatching{JSONObject(s).optString("name",n)}.getOrDefault(n);showDashboard()}
                else feedback.text=runCatching{JSONObject(s).optString("error","Credenziali non valide")}.getOrDefault("Credenziali non valide")
            }}
        }); root.addView(feedback)
        root.addView(button("Torna ad autista"){finish()})
    }

    private fun showDashboard(){
        root.removeAllViews(); root.addView(title("Area Responsabile",30f)); root.addView(msg("Responsabile: $manager"))
        val stats=msg("Caricamento dati…"); val active=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        root.addView(stats); root.addView(title("Chiavi attualmente fuori",20f)); root.addView(active)
        Api.get("/manager-dashboard"){c,s->runOnUiThread{
            if(c !in 200..299){stats.text="Errore caricamento dashboard";return@runOnUiThread}
            runCatching{JSONObject(s)}.getOrNull()?.let{d->
                val st=d.optJSONObject("stats"); stats.text="Assegnate ${st?.optInt("assigned",0)}   •   Disponibili ${st?.optInt("available",0)}   •   Autisti ${st?.optInt("drivers",0)}"
                val a=d.optJSONArray("active")?:JSONArray(); active.removeAllViews()
                if(a.length()==0) active.addView(msg("Nessuna chiave attualmente assegnata."))
                for(i in 0 until a.length()){
                    val x=a.getJSONObject(i); val row=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,10,0,10)}
                    row.addView(msg("${x.optString("name")} · ${x.optInt("driver_code")}\n${x.optString("plate")} · ${x.optString("vehicle_type")}"))
                    row.addView(button("Chiudi assegnazione"){askClose(x.optString("id"),active,stats)})
                    active.addView(row)
                }
            }
        }}
        addHabitualSection(); addHistorySection(); addPhotoSection()
        root.addView(button("Esci area responsabile"){manager="";showLogin()})
    }

    private fun askClose(id:String, active:LinearLayout, stats:TextView){
        val e=input("Motivo della chiusura")
        AlertDialog.Builder(this).setTitle("Chiusura manuale").setView(e).setPositiveButton("Chiudi"){_,_->
            val reason=e.text.toString().trim(); if(reason.isBlank())return@setPositiveButton
            Api.postJson("/manager-close",JSONObject().put("id",id).put("manager",manager).put("reason",reason)){c,_->runOnUiThread{if(c in 200..299)showDashboard() else Toast.makeText(this,"Chiusura non riuscita",Toast.LENGTH_SHORT).show()}}
        }.setNegativeButton("Annulla",null).show()
    }

    private fun addHabitualSection(){
        root.addView(title("Furgoni abituali",20f)); root.addView(msg("Configura il mezzo normalmente utilizzato da ciascun autista."))
        val ds=Spinner(this); val vs=Spinner(this); val feedback=msg("")
        root.addView(ds);root.addView(vs)
        Api.get("/manager-habitual"){c,s->runOnUiThread{
            if(c !in 200..299){feedback.text="Errore caricamento furgoni";return@runOnUiThread}
            val d=runCatching{JSONObject(s)}.getOrNull()?:return@runOnUiThread
            habitualDrivers=d.optJSONArray("drivers")?:JSONArray(); habitualVehicles=d.optJSONArray("vehicles")?:JSONArray()
            val dl=mutableListOf("Seleziona autista"); for(i in 0 until habitualDrivers.length()){val x=habitualDrivers.getJSONObject(i);dl.add("${x.optString("name")} · ${x.optInt("code")}")}
            val vl=mutableListOf("Nessun furgone abituale"); for(i in 0 until habitualVehicles.length()){val x=habitualVehicles.getJSONObject(i);vl.add("${x.optString("plate")} · ${x.optString("vehicle_type")}")}
            ds.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,dl);vs.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,vl)
            ds.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{override fun onNothingSelected(p:android.widget.AdapterView<*>?){};override fun onItemSelected(p:android.widget.AdapterView<*>?,v:View?,pos:Int,id:Long){if(pos<=0)return;val plate=habitualDrivers.getJSONObject(pos-1).optString("habitual_plate");var vp=0;for(i in 0 until habitualVehicles.length())if(habitualVehicles.getJSONObject(i).optString("plate")==plate)vp=i+1;vs.setSelection(vp)}}
        }}
        root.addView(button("Salva furgone abituale"){
            if(ds.selectedItemPosition<=0){feedback.text="Seleziona un autista";return@button}
            val code=habitualDrivers.getJSONObject(ds.selectedItemPosition-1).optInt("code");val plate=if(vs.selectedItemPosition<=0)"" else habitualVehicles.getJSONObject(vs.selectedItemPosition-1).optString("plate")
            Api.postJson("/manager-habitual",JSONObject().put("driver_code",code).put("plate",plate).put("manager",manager)){c,s->runOnUiThread{feedback.text=if(c in 200..299)"Furgone abituale salvato" else runCatching{JSONObject(s).optString("error")}.getOrDefault("Errore")}}
        });root.addView(feedback)
    }

    private fun addHistorySection(){
        root.addView(title("Ricerca storico / multa",20f));val plate=input("Targa");val date=input("Data (AAAA-MM-GG)");val out=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(plate);root.addView(date)
        root.addView(button("Cerca storico"){
            val q=mutableListOf<String>();if(plate.text.isNotBlank())q.add("plate="+URLEncoder.encode(plate.text.toString(),"UTF-8"));if(date.text.isNotBlank())q.add("date="+URLEncoder.encode(date.text.toString(),"UTF-8"))
            Api.get("/manager-history?"+q.joinToString("&")){c,s->runOnUiThread{out.removeAllViews();if(c !in 200..299){out.addView(msg("Errore ricerca"));return@runOnUiThread};val a=runCatching{JSONArray(s)}.getOrNull()?:JSONArray();if(a.length()==0)out.addView(msg("Nessuna assegnazione trovata."));for(i in 0 until a.length()){val x=a.getJSONObject(i);out.addView(msg("${x.optString("plate")} · ${x.optString("name")} (${x.optInt("driver_code")})\n${x.optString("checkout_at")} → ${x.optString("return_at","IN CORSO")}"))}}}
        });root.addView(out)
    }

    private fun addPhotoSection(){
        root.addView(title("Foto cambi furgone",20f));val plate=input("Targa");val date=input("Data (AAAA-MM-GG)");val out=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(plate);root.addView(date)
        root.addView(button("Cerca foto"){
            val q=mutableListOf<String>();if(plate.text.isNotBlank())q.add("plate="+URLEncoder.encode(plate.text.toString(),"UTF-8"));if(date.text.isNotBlank())q.add("date="+URLEncoder.encode(date.text.toString(),"UTF-8"))
            Api.get("/manager-photos?"+q.joinToString("&")){c,s->runOnUiThread{out.removeAllViews();if(c !in 200..299){out.addView(msg("Errore ricerca foto"));return@runOnUiThread};val a=runCatching{JSONArray(s)}.getOrNull()?:JSONArray();if(a.length()==0)out.addView(msg("Nessuna foto trovata."));for(i in 0 until a.length()){val x=a.getJSONObject(i);out.addView(msg("${x.optString("plate")} · ${x.optString("name")} (${x.optInt("driver_code")})\n${x.optString("created_at")}\n${x.optString("photo_url")}"))}}}
        });root.addView(out)
    }
}
