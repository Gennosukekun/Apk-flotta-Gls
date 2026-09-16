package it.gestionechiavi.mezzi

import android.app.*
import android.content.*
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.json.*

data class Driver(val code:Int,val name:String,val label:String){
    override fun toString()="$name · $label"
}

class MainActivity:AppCompatActivity(){
    private lateinit var prefs:android.content.SharedPreferences
    private var drivers=listOf<Driver>()
    private var currentAssignment:String?=null
    private val scanLauncher=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){r->
        if(r.resultCode==RESULT_OK) r.data?.getStringExtra("qr")?.let{submitScan(it)}
    }

    override fun onCreate(b:Bundle?){
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        prefs=getSharedPreferences("device",MODE_PRIVATE)
        findViewById<Button>(R.id.scan).setOnClickListener{
            if(prefs.getInt("driver",0)==0) Toast.makeText(this,"Prima associa l'autista",Toast.LENGTH_SHORT).show()
            else scanLauncher.launch(Intent(this,ScannerActivity::class.java))
        }
        findViewById<Button>(R.id.saveDriver).setOnClickListener{
            val d=drivers.getOrNull(findViewById<Spinner>(R.id.driverSpinner).selectedItemPosition)
            if(d==null){
                findViewById<TextView>(R.id.message).text="Elenco autisti non disponibile. Riprova tra qualche secondo."
                return@setOnClickListener
            }
            prefs.edit().putInt("driver",d.code).putString("driver_name",d.name).putString("driver_label",d.label).apply()
            render();refresh()
        }
        findViewById<Button>(R.id.photo).setOnClickListener{
            startActivity(Intent(this,PhotoActivity::class.java).putExtra("assignment_id",currentAssignment).putExtra("driver_code",prefs.getInt("driver",0)))
        }
        findViewById<Button>(R.id.changeDriver).setOnClickListener{
            val e=EditText(this);e.inputType=2
            AlertDialog.Builder(this).setTitle("Autorizzazione responsabile").setView(e).setPositiveButton("Verifica"){_,_->verify(e.text.toString())}.setNegativeButton("Annulla",null).show()
        }
        load()
    }

    private fun load(){
        findViewById<TextView>(R.id.message).text="Caricamento autisti…"
        Api.get("/drivers"){c,s->runOnUiThread{
            if(c !in 200..299){
                findViewById<TextView>(R.id.message).text="Impossibile caricare gli autisti (errore $c)."
                return@runOnUiThread
            }
            try{
                val trimmed=s.trim()
                val a=when{
                    trimmed.startsWith("[")->JSONArray(trimmed)
                    trimmed.startsWith("{")->{
                        val root=JSONObject(trimmed)
                        root.optJSONArray("drivers") ?: root.optJSONArray("data") ?: root.optJSONArray("items") ?: JSONArray()
                    }
                    else->JSONArray()
                }
                drivers=(0 until a.length()).mapNotNull{i->
                    val o=a.optJSONObject(i)?:return@mapNotNull null
                    val code=o.optInt("code",0)
                    val name=o.optString("name","").trim()
                    if(code==0||name.isBlank()) return@mapNotNull null
                    val label=if(o.isNull("code_label")||o.optString("code_label").isBlank()) code.toString() else o.optString("code_label")
                    Driver(code,name,label)
                }
                findViewById<Spinner>(R.id.driverSpinner).adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,drivers)
                findViewById<TextView>(R.id.message).text=if(drivers.isEmpty()) "Nessun autista disponibile." else ""
                render();refresh()
            }catch(e:Exception){
                drivers=emptyList()
                findViewById<TextView>(R.id.message).text="Errore nel caricamento dell'elenco autisti."
            }
        }}
    }

    private fun render(){
        val c=prefs.getInt("driver",0)
        findViewById<TextView>(R.id.driver).text=if(c==0)"Autista non configurato" else "Autista: ${prefs.getString("driver_name","")} · ${prefs.getString("driver_label",c.toString())}"
        findViewById<Spinner>(R.id.driverSpinner).visibility=if(c==0)View.VISIBLE else View.GONE
        findViewById<Button>(R.id.saveDriver).visibility=if(c==0)View.VISIBLE else View.GONE
    }

    private fun refresh(){
        val c=prefs.getInt("driver",0);if(c==0)return
        Api.get("/assignment-current?driver_code=$c"){status,s->runOnUiThread{
            if(status !in 200..299)return@runOnUiThread
            val root=runCatching{JSONObject(s)}.getOrNull()
            val o=root?.optJSONObject("assignment") ?: root
            if(o==null||!o.has("id")||o.isNull("id")){
                currentAssignment=null
                findViewById<TextView>(R.id.status).text="Nessuna chiave assegnata"
                findViewById<Button>(R.id.photo).visibility=View.GONE
            }else{
                currentAssignment=o.optString("id")
                findViewById<TextView>(R.id.status).text="Chiave assegnata: ${o.optString("plate")}"
                val changed=root?.optBoolean("changed_vehicle",o.optBoolean("changed_vehicle",false)) ?: false
                findViewById<Button>(R.id.photo).visibility=if(changed)View.VISIBLE else View.GONE
            }
        }}
    }

    private fun submitScan(q:String){
        Api.postJson("/scan",JSONObject().put("driver_code",prefs.getInt("driver",0)).put("qr_code",q)){_,s->runOnUiThread{
            val o=runCatching{JSONObject(s)}.getOrNull()
            findViewById<TextView>(R.id.message).text=o?.optString("message",o.optString("error","Operazione completata"))?:"Errore"
            refresh()
        }}
    }

    private fun verify(pin:String){
        Api.postJson("/manager-login",JSONObject().put("pin",pin)){c,_->runOnUiThread{
            if(c in 200..299){prefs.edit().clear().apply();render();load()}
            else findViewById<TextView>(R.id.message).text="Codice responsabile non valido"
        }}
    }

    override fun onResume(){super.onResume();if(::prefs.isInitialized)refresh()}
}
