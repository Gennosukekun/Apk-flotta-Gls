package it.gestionechiavi.mezzi

import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

class ManagerActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private var manager = ""
    private var managerToken = ""
    private var drivers = JSONArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 100, 40, 40)
        }
        setContentView(ScrollView(this).apply { addView(root) })
        showLogin()
    }

    private fun tv(value: String, size: Float = 18f) = TextView(this).apply {
        text = value
        textSize = size
        setPadding(0, 12, 0, 12)
    }

    private fun input(hintText: String, password: Boolean = false) = EditText(this).apply {
        hint = hintText
        if (password) {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
    }

    private fun btn(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun showLogin() {
        root.removeAllViews()
        root.addView(tv("GESTIONE FLOTTA", 12f))
        root.addView(tv("Area Responsabile", 30f))
        root.addView(tv("Usa il tuo nome e PIN personale."))
        val name = input("Nome responsabile")
        val pin = input("PIN", true)
        val message = tv("")
        root.addView(name)
        root.addView(pin)
        root.addView(btn("Accedi") {
            val body = JSONObject()
                .put("name", name.text.toString().trim())
                .put("pin", pin.text.toString().trim())
            Api.postJson("/manager-login", body) { code, response ->
                runOnUiThread {
                    if (code in 200..299) {
                        val obj = JSONObject(response)
                        manager = obj.optString("name")
                        managerToken = obj.optString("token")
                        showDashboard()
                    } else {
                        message.text = runCatching { JSONObject(response).optString("error") }
                            .getOrDefault("Accesso non riuscito")
                    }
                }
            }
        })
        root.addView(message)
        root.addView(btn("Torna ad autista") { finish() })
    }

    private fun showDashboard() {
        root.removeAllViews()
        root.addView(tv("Area Responsabile", 30f))
        root.addView(tv("Responsabile: $manager"))
        val status = tv("Caricamento dashboard…")
        root.addView(status)
        Api.get("/manager-dashboard", managerToken) { code, response ->
            runOnUiThread {
                status.text = if (code in 200..299) {
                    val stats = JSONObject(response).optJSONObject("stats")
                    "Chiavi assegnate: ${stats?.optInt("assigned", 0)} · Disponibili: ${stats?.optInt("available", 0)} · Autisti: ${stats?.optInt("drivers", 0)}"
                } else "Dashboard non disponibile ($code)"
            }
        }
        root.addView(tv("Associa questo dispositivo", 22f))
        root.addView(tv("Seleziona l'autista che userà permanentemente questo telefono. Una nuova associazione sostituisce quella precedente per lo stesso autista."))
        val spinner = Spinner(this)
        val feedback = tv("")
        root.addView(spinner)
        Api.get("/manager-habitual", managerToken) { code, response ->
            runOnUiThread {
                if (code in 200..299) {
                    drivers = JSONObject(response).optJSONArray("drivers") ?: JSONArray()
                    val names = (0 until drivers.length()).map { index ->
                        val driver = drivers.getJSONObject(index)
                        val codeValue = driver.optInt("code")
                        val label = driver.optString("code_label").ifBlank { codeValue.toString() }
                        "${driver.optString("name")} · $label"
                    }
                    spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
                } else feedback.text = "Impossibile caricare autisti ($code)"
            }
        }
        root.addView(btn("Associa telefono all'autista") {
            if (drivers.length() == 0) return@btn
            val driver = drivers.getJSONObject(spinner.selectedItemPosition)
            val body = JSONObject()
                .put("driver_code", driver.optInt("code"))
                .put("device_label", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            Api.postJson("/device-register", body, managerToken) { code, response ->
                runOnUiThread {
                    if (code in 200..299) {
                        val obj = JSONObject(response)
                        val registeredDriver = obj.getJSONObject("driver")
                        val codeValue = registeredDriver.optInt("code")
                        val label = registeredDriver.optString("code_label").ifBlank { codeValue.toString() }
                        getSharedPreferences("device", MODE_PRIVATE).edit()
                            .putString("device_token", obj.getString("device_token"))
                            .putInt("driver", codeValue)
                            .putString("driver_name", registeredDriver.optString("name"))
                            .putString("driver_label", label)
                            .apply()
                        feedback.text = "Dispositivo associato a ${registeredDriver.optString("name")}"
                    } else feedback.text = runCatching { JSONObject(response).optString("error") }.getOrDefault("Associazione non riuscita")
                }
            }
        })
        root.addView(feedback)
        root.addView(btn("Torna ad autista") { finish() })
        root.addView(btn("Esci area responsabile") {
            manager = ""
            managerToken = ""
            showLogin()
        })
    }
}
