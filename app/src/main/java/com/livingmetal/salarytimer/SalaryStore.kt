package com.livingmetal.salarytimer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

class SalaryStore(context: Context) {
    private val p=context.getSharedPreferences("s",Context.MODE_PRIVATE)
    fun load(): SalaryData {
        val text=p.getString("data_v14",null)
        if(text!=null)runCatching{return decode(text)}
        val st=runCatching{LocalTime.parse(p.getString("start","09:00"))}.getOrDefault(LocalTime.of(9,0))
        val rawEnd=runCatching{LocalTime.parse(p.getString("end","18:00"))}.getOrDefault(LocalTime.of(18,0))
        val en=if(rawEnd>st)rawEnd else LocalTime.of(18,0)
        var c=SalaryConfig(annual=p.getLong("annual",60_000_000).coerceIn(1,1_000_000_000_000),payday=p.getInt("payday",25).coerceIn(1,31),start=st.toString(),end=en.toString(),breakMinutes=p.getInt("lunch",60).coerceIn(0,720))
        if(SalaryEngine.validate(c)!=null)c=c.copy(breakMinutes=0)
        if(SalaryEngine.validate(c)!=null)c=c.copy(start="09:00",end="18:00",breakMinutes=60)
        return SalaryData(c)
    }
    fun save(d: SalaryData) {
        require(SalaryEngine.validate(d.config)==null)
        require(d.work.values.all{SalaryEngine.validate(it)==null})
        p.edit().putString("data_v14",encode(d)).putLong("annual",d.config.annual).putInt("payday",d.config.payday)
            .putString("start",d.config.start).putString("end",d.config.end).putInt("lunch",d.config.breakMinutes).apply()
    }
    companion object {
        fun encode(d: SalaryData): String {
            val c=d.config
            val root=JSONObject().put("schema",14).put("config",JSONObject().put("annual",c.annual).put("payday",c.payday)
                .put("start",c.start).put("end",c.end).put("breakStart",c.breakStart).put("breakMinutes",c.breakMinutes)
                .put("theme",c.theme).put("calendarEnabled",c.calendarEnabled).put("extraHourly",c.extraHourly))
            root.put("work",JSONArray().apply{d.work.values.sortedBy{it.date}.forEach{e->put(JSONObject().put("date",e.date.toString()).put("rest",e.rest).put("hours",e.extraHours).put("multiplier",e.multiplier).put("note",e.note))}})
            root.put("bonuses",JSONArray().apply{d.bonuses.sortedBy{it.date}.forEach{b->put(JSONObject().put("id",b.id).put("date",b.date.toString()).put("amount",b.amount).put("title",b.title).put("paid",b.paid))}})
            return root.toString(2)
        }
        fun decode(text: String): SalaryData {
            require(text.length<2_000_000)
            val r=JSONObject(text);require(r.optInt("schema")==14)
            val c=r.getJSONObject("config")
            val cfg=SalaryConfig(c.getLong("annual"),c.getInt("payday"),c.getString("start"),c.getString("end"),c.getString("breakStart"),c.getInt("breakMinutes"),c.optString("theme","system"),c.optBoolean("calendarEnabled",false),c.optLong("extraHourly",0))
            require(SalaryEngine.validate(cfg)==null)
            val work=mutableMapOf<LocalDate,WorkEntry>();val entries=r.getJSONArray("work")
            for(i in 0 until entries.length()){
                val e=entries.getJSONObject(i);val date=LocalDate.parse(e.getString("date"))
                val item=WorkEntry(date,e.getBoolean("rest"),e.getDouble("hours"),e.getDouble("multiplier"),e.optString("note",""))
                require(SalaryEngine.validate(item)==null);work[date]=item
            }
            val bonuses=mutableListOf<Bonus>();val values=r.getJSONArray("bonuses")
            for(i in 0 until values.length()){
                val b=values.getJSONObject(i);val amount=b.getLong("amount");require(amount in 1..1_000_000_000_000L)
                bonuses.add(Bonus(b.getString("id"),LocalDate.parse(b.getString("date")),amount,b.getString("title").take(60),b.getBoolean("paid")))
            }
            require(bonuses.map{it.id}.distinct().size==bonuses.size)
            return SalaryData(cfg,work,bonuses)
        }
    }
}
