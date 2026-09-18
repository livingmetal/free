package com.livingmetal.salarytimer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.*

data class PhoneEvent(val id: Long,val title: String,val begin: Long,val end: Long,val allDay: Boolean) {
    fun intersects(date: LocalDate,zone: ZoneId=ZoneId.systemDefault()): Boolean {
        val z=if(allDay)ZoneOffset.UTC else zone
        val first=Instant.ofEpochMilli(begin).atZone(z).toLocalDate()
        val last=Instant.ofEpochMilli(maxOf(begin,end-1)).atZone(z).toLocalDate()
        return date>=first && date<=last
    }
    fun timeLabel(): String=if(allDay)"종일" else Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault()).toLocalTime().toString().take(5)
}
object PhoneCalendar {
    fun permitted(c: Context)=ContextCompat.checkSelfPermission(c,Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED
    /** Query on Dispatchers.IO, after explicit opt-in and permission. Events are not persisted. */
    fun read(c: Context,m: YearMonth): List<PhoneEvent> {
        check(permitted(c))
        val zone=ZoneId.systemDefault()
        val begin=m.atDay(1).minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val end=m.plusMonths(1).atDay(1).plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri=CalendarContract.Instances.CONTENT_URI.buildUpon().apply{ContentUris.appendId(this,begin);ContentUris.appendId(this,end)}.build()
        val projection=arrayOf(CalendarContract.Instances.EVENT_ID,CalendarContract.Instances.TITLE,CalendarContract.Instances.BEGIN,CalendarContract.Instances.END,CalendarContract.Instances.ALL_DAY)
        val result=mutableListOf<PhoneEvent>()
        c.contentResolver.query(uri,projection,"${CalendarContract.Instances.VISIBLE} = 1",null,"${CalendarContract.Instances.BEGIN} ASC")?.use{cursor->
            while(cursor.moveToNext())result.add(PhoneEvent(cursor.getLong(0),cursor.getString(1)?:"제목 없는 일정",cursor.getLong(2),cursor.getLong(3),cursor.getInt(4)==1))
        }
        return result.distinctBy{Pair(it.id,it.begin)}
    }
    fun view(c: Context,e: PhoneEvent): Boolean=runCatching{
        c.startActivity(Intent(Intent.ACTION_VIEW,ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI,e.id)).putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,e.begin).putExtra(CalendarContract.EXTRA_EVENT_END_TIME,e.end));true
    }.getOrDefault(false)
    /** Calendar app owns the final confirmation; no silent writes or wage amounts. */
    fun add(c: Context,date: LocalDate,hours: Double): Boolean=runCatching{
        val start=date.atTime(9,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        c.startActivity(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE,if(hours>0)"추가 근무" else "근무 메모")
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME,start+(if(hours>0)hours else 1.0).times(3600000).toLong()));true
    }.getOrDefault(false)
}
