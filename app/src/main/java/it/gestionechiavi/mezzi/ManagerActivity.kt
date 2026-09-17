package it.gestionechiavi.mezzi

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

class ManagerActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private var manager = ""
    private var managerToken = ""
    private var drivers = JSONArray()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun color(id: Int) = getColor(id)
    private fun shape(fill: Int, radius: Int = 18, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat(); stroke?.let { setStroke(dp(1), it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(52), dp(20), dp(28))
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(color(R.color.app_background)); isFillViewport = true; addView(root)
        })
        showLogin()
    }

    private fun tv(value: String, size: Float = 16f, primary: Boolean = true, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color(if (primary) R.color.text_primary else R.color.text_secondary))
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun title(value: String) = tv(value, 32f, true, true).apply { setPadding(0, dp(3), 0, dp(4)) }
    private fun eyebrow(value: String) = tv(value, 12f, false, true).apply { letterSpacing = .12f }

    private fun input(hintText: String, password: Boolean = false) = EditText(this).apply {
        hint = hintText; setHintTextColor(color(R.color.text_secondary)); setTextColor(color(R.color.text_primary)); textSize = 16f
        setPadding(dp(16), 0, dp(16), 0); background = shape(color(R.color.surface_input), 14, color(R.color.border_input))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(12) }
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun button(label: String, primary: Boolean = true, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 16f; setTypeface(typeface, Typeface.BOLD)
        setTextColor(if (primary) Color.WHITE else color(R.color.text_primary))
        backgroundTintList = android.content.res.ColorStateList.valueOf(color(if (primary) R.color.primary else R.color.secondary_button))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(12) }
        setOnClickListener { action() }
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(20)); background = shape(color(R.color.surface), 20, color(R.color.border))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18) }
    }

    private fun showLogin() {
        root.removeAllViews()
        root.addView(eyebrow("GESTIONE FLOTTA"))
        root.addView(title("Area Responsabile"))
        root.addView(tv("Accesso riservato alla gestione della flotta.", 14f, false))
        val loginCard = card()
        loginCard.addView(tv("ACCESSO RESPONSABILE", 11f, false, true))
        val name = input("Nome responsabile")
        val pin = input("PIN", true)
        val message = tv("", 14f, false, true).apply { setPadding(0, dp(12), 0, 0) }
        loginCard.addView(name); loginCard.addView(pin)
        loginCard.addView(button("Accedi") {
            val body = JSONObject().put("name", name.text.toString().trim()).put("pin", pin.text.toString().trim())
            Api.postJson("/manager-login", body) { code, response -> runOnUiThread {
                if (code in 200..299) {
                    val obj = JSONObject(response); manager = obj.optString("name"); managerToken = obj.optString("token"); showDashboard()
                } else message.text = runCatching { JSONObject(response).optString("error") }.getOrDefault("Accesso non riuscito")
            }}
        })
        loginCard.addView(message)
        root.addView(loginCard)
        root.addView(button("Torna ad autista", false) { finish() })
    }

    private fun showDashboard() {
        root.removeAllViews()
        root.addView(eyebrow("GESTIONE FLOTTA")); root.addView(title("Dashboard")); root.addView(tv("Responsabile: $manager", 14f, false))

        val statsCard = card(); statsCard.addView(tv("PANORAMICA", 11f, false, true))
        val status = tv("Caricamento dati…", 19f, true, true).apply { setPadding(0, dp(10), 0, 0) }; statsCard.addView(status); root.addView(statsCard)
        Api.get("/manager-dashboard", managerToken) { code, response -> runOnUiThread {
            status.text = if (code in 200..299) {
                val stats = JSONObject(response).optJSONObject("stats")
                "${stats?.optInt("assigned",0)} chiavi fuori   •   ${stats?.optInt("available",0)} disponibili\n${stats?.optInt("drivers",0)} autisti attivi"
            } else "Dashboard non disponibile ($code)"
        }}

        val deviceCard = card(); deviceCard.addView(tv("ASSOCIA DISPOSITIVO", 11f, false, true)); deviceCard.addView(tv("Collega questo telefono all'autista che lo utilizzerà.", 14f, false).apply { setPadding(0, dp(7), 0, 0) })
        val spinner = Spinner(this).apply {
            background = shape(color(R.color.surface_input), 14, color(R.color.border_input)); setPadding(dp(14),0,dp(14),0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin=dp(14) }
        }
        val feedback = tv("", 14f, false, true).apply { setPadding(0,dp(10),0,0) }
        deviceCard.addView(spinner)
        Api.get("/manager-habitual", managerToken) { code, response -> runOnUiThread {
            if (code in 200..299) {
                drivers = JSONObject(response).optJSONArray("drivers") ?: JSONArray()
                val names = (0 until drivers.length()).map { i -> val d=drivers.getJSONObject(i); val c=d.optInt("code"); val l=d.optString("code_label").ifBlank{c.toString()}; "${d.optString("name")} · $l" }
                spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
            } else feedback.text = "Impossibile caricare autisti ($code)"
        }}
        deviceCard.addView(button("Associa telefono") {
            if (drivers.length()==0) return@button
            val d=drivers.getJSONObject(spinner.selectedItemPosition)
            val body=JSONObject().put("driver_code",d.optInt("code")).put("device_label","${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            Api.postJson("/device-register",body,managerToken){code,response->runOnUiThread{
                if(code in 200..299){
                    val obj=JSONObject(response); val rd=obj.getJSONObject("driver"); val c=rd.optInt("code"); val l=rd.optString("code_label").ifBlank{c.toString()}
                    getSharedPreferences("device",MODE_PRIVATE).edit().putString("device_token",obj.getString("device_token")).putInt("driver",c).putString("driver_name",rd.optString("name")).putString("driver_label",l).apply()
                    feedback.text="Dispositivo associato a ${rd.optString("name")}"
                } else feedback.text=runCatching{JSONObject(response).optString("error")}.getOrDefault("Associazione non riuscita")
            }}
        })
        deviceCard.addView(feedback); root.addView(deviceCard)
        root.addView(button("Torna ad autista", false){finish()})
        root.addView(button("Esci dall'area responsabile", false){manager="";managerToken="";showLogin()})
    }
}
