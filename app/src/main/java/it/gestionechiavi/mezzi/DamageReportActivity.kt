package it.gestionechiavi.mezzi

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class DamageReportActivity:AppCompatActivity(){
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun shape(fill:Int,r:Int=16,stroke:Int?=null)=GradientDrawable().apply{setColor(fill);cornerRadius=dp(r).toFloat();stroke?.let{setStroke(dp(1),it)}}
 override fun onCreate(b:Bundle?){super.onCreate(b);val token=intent.getStringExtra("device_token").orEmpty();val plate=intent.getStringExtra("plate").orEmpty();val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(52),dp(20),dp(28))};setContentView(ScrollView(this).apply{setBackgroundColor(getColor(R.color.app_background));addView(root)});fun tv(s:String,z:Float=16f,b:Boolean=false)=TextView(this).apply{text=s;textSize=z;setTextColor(getColor(R.color.text_primary));if(b)setTypeface(typeface,Typeface.BOLD);setPadding(0,dp(5),0,dp(5))};root.addView(tv("SEGNALAZIONE MEZZO",12f,true));root.addView(tv("Danno / anomalia",30f,true));root.addView(tv("Furgone assegnato: $plate",17f,true));val categories=arrayOf("Carrozzeria","Pneumatici","Luci","Spie/guasto","Interni","Pulizia","Altro");val spinner=Spinner(this).apply{adapter=ArrayAdapter(this@DamageReportActivity,android.R.layout.simple_spinner_dropdown_item,categories);background=shape(getColor(R.color.surface_input),14,getColor(R.color.border_input));layoutParams=LinearLayout.LayoutParams(-1,dp(54)).apply{topMargin=dp(18)}};root.addView(spinner);val desc=EditText(this).apply{hint="Descrivi il danno o l'anomalia";gravity=android.view.Gravity.TOP;minLines=5;setTextColor(getColor(R.color.text_primary));setHintTextColor(getColor(R.color.text_secondary));background=shape(getColor(R.color.surface_input),14,getColor(R.color.border_input));setPadding(dp(14),dp(14),dp(14),dp(14));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(12)}};root.addView(desc);val msg=tv("",14f,true);val save=Button(this).apply{text="Invia segnalazione";isAllCaps=false;setTextColor(Color.WHITE);setTypeface(typeface,Typeface.BOLD);backgroundTintList=android.content.res.ColorStateList.valueOf(getColor(R.color.primary));layoutParams=LinearLayout.LayoutParams(-1,dp(56)).apply{topMargin=dp(14)}};root.addView(save);root.addView(msg);save.setOnClickListener{val text=desc.text.toString().trim();if(text.isBlank()){msg.text="Inserisci una descrizione";return@setOnClickListener};save.isEnabled=false;Api.postJson("/damage-reports",JSONObject().put("category",spinner.selectedItem.toString()).put("description",text),token){code,r->runOnUiThread{if(code in 200..299){msg.text="Segnalazione inviata e associata a $plate";desc.isEnabled=false;spinner.isEnabled=false;save.visibility=View.GONE}else{msg.text=runCatching{JSONObject(r).optString("error")}.getOrNull()?.takeIf{it.isNotBlank()}?:"Errore invio ($code)";save.isEnabled=true}}}}}
}
