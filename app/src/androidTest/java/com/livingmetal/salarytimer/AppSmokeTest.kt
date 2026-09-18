package com.livingmetal.salarytimer

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.*

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val ctx get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun seed(){
        ctx.getSharedPreferences("s",Context.MODE_PRIVATE).edit().clear().commit()
        SalaryStore(ctx).save(SalaryData(SalaryConfig(annual=60_000_000)))
        ui.activityRule.scenario.recreate()
    }
    private fun capture(name: String){
        ui.waitForIdle()
        val folder=File(ctx.getExternalFilesDir(null),"screenshots");folder.mkdirs()
        assertTrue(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(folder,"$name.png")))
    }
    @Test fun oldPreferencesMigrateAndSurviveThemeChange(){
        ctx.getSharedPreferences("s",0).edit().clear().putLong("annual",72_000_000).putInt("payday",20).putString("start","08:30").putString("end","17:30").putInt("lunch",60).commit()
        ui.activityRule.scenario.recreate()
        val loaded=SalaryStore(ctx).load();assertEquals(72_000_000,loaded.config.annual);assertEquals("08:30",loaded.config.start)
        ui.onNodeWithTag("quick_theme").performClick()
        ui.onNodeWithTag("app_dark").assertExists()
        ui.activityRule.scenario.recreate()
        assertEquals("dark",SalaryStore(ctx).load().config.theme)
        assertEquals(72_000_000,SalaryStore(ctx).load().config.annual)
        capture("dark-month")
    }
    @Test fun weekendBonusCalendarAndYearWorkTogether(){
        val saturday=SalaryEngine.monthDays(YearMonth.now()).first{it.dayOfWeek==DayOfWeek.SATURDAY}
        ui.onNodeWithTag("nav_calendar").performClick()
        ui.onNodeWithTag("day_$saturday").performScrollTo().performClick()
        ui.onNodeWithTag("edit_work").performScrollTo().performClick()
        ui.onNodeWithTag("work_hours").performScrollTo().performTextReplacement("4.5")
        ui.onNodeWithTag("work_multiplier").performScrollTo().performTextReplacement("1.5")
        ui.onNodeWithTag("work_note").performScrollTo().performTextReplacement("weekend test")
        ui.onNodeWithTag("save_work").performClick()
        ui.waitForIdle()
        assertEquals(4.5,SalaryStore(ctx).load().work[saturday]!!.extraHours,0.001)
        ui.onNodeWithTag("add_bonus_calendar").performScrollTo().performClick()
        ui.onNodeWithTag("bonus_title").performScrollTo().performTextReplacement("Bonus test")
        ui.onNodeWithTag("bonus_amount").performScrollTo().performTextReplacement("1000000")
        ui.onNodeWithTag("save_bonus").performClick()
        ui.waitForIdle()
        assertEquals(1,SalaryStore(ctx).load().bonuses.size)
        assertEquals(saturday,SalaryStore(ctx).load().bonuses[0].date)
        ui.onNodeWithTag("day_$saturday").performScrollTo()
        capture("calendar")
        ui.onNodeWithTag("nav_year").performClick()
        ui.onNodeWithTag("year_total").assertExists()
        capture("year")
        ui.onNodeWithTag("nav_timer").performClick()
        ui.onNodeWithTag("mode_today").performClick()
        ui.onNodeWithTag("main_amount").assertExists()
        capture("light-today")
        ui.activityRule.scenario.recreate()
        assertEquals(4.5,SalaryStore(ctx).load().work[saturday]!!.extraHours,0.001)
        assertEquals(1,SalaryStore(ctx).load().bonuses.size)
    }
    @Test fun invalidHoursAreNotSaved(){
        ui.onNodeWithTag("quick_work").performScrollTo().performClick()
        ui.onNodeWithTag("work_hours").performScrollTo().performTextReplacement("25")
        ui.onNodeWithTag("save_work").performClick()
        ui.onNodeWithTag("work_error").assertExists()
        assertTrue(SalaryStore(ctx).load().work.isEmpty())
    }
    @Test fun calendarIsOptionalAndDoesNotTriggerPermissionsAtStartup(){
        assertFalse(SalaryStore(ctx).load().config.calendarEnabled)
        ui.onNodeWithTag("nav_calendar").performClick()
        ui.onNodeWithTag("edit_work").performScrollTo().assertExists()
        assertFalse(SalaryStore(ctx).load().config.calendarEnabled)
    }
    @Test fun persistenceRoundTripIncludesThemeBonusAndWeekend(){
        val date=LocalDate.now()
        val d=SalaryData(SalaryConfig(theme="dark",extraHourly=22_000),mapOf(date to WorkEntry(date,true,2.5,1.5,"note")),listOf(Bonus("abc",date,150_000,"test",true)))
        SalaryStore(ctx).save(d)
        assertEquals(d,SalaryStore(ctx).load())
        assertEquals(d,SalaryStore.decode(SalaryStore.encode(d)))
    }
    @Test fun localCalendarProviderReturnsEventsAfterPermission(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val automation=instrumentation.uiAutomation
        automation.executeShellCommand("pm grant ${ctx.packageName} android.permission.READ_CALENDAR").use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}
        automation.adoptShellPermissionIdentity(Manifest.permission.READ_CALENDAR,Manifest.permission.WRITE_CALENDAR)
        val calendarUri=CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER,"true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME,"SalaryTimer-TEST")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE,CalendarContract.ACCOUNT_TYPE_LOCAL).build()
        var inserted: android.net.Uri?=null
        try {
            val values=ContentValues().apply{
                put(CalendarContract.Calendars.ACCOUNT_NAME,"SalaryTimer-TEST")
                put(CalendarContract.Calendars.ACCOUNT_TYPE,CalendarContract.ACCOUNT_TYPE_LOCAL)
                put(CalendarContract.Calendars.NAME,"SalaryTimer test calendar")
                put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,"SalaryTimer test calendar")
                put(CalendarContract.Calendars.CALENDAR_COLOR,0xff22bb88.toInt())
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT,"SalaryTimer-TEST")
                put(CalendarContract.Calendars.VISIBLE,1);put(CalendarContract.Calendars.SYNC_EVENTS,1)
            }
            inserted=ctx.contentResolver.insert(calendarUri,values)!!
            val id=android.content.ContentUris.parseId(inserted)
            val start=LocalDate.now().atTime(10,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI,ContentValues().apply{
                put(CalendarContract.Events.CALENDAR_ID,id);put(CalendarContract.Events.TITLE,"SalaryTimer provider test")
                put(CalendarContract.Events.DTSTART,start);put(CalendarContract.Events.DTEND,start+3600000)
                put(CalendarContract.Events.EVENT_TIMEZONE,ZoneId.systemDefault().id)
            })
            val result=PhoneCalendar.read(ctx,YearMonth.now())
            assertTrue(result.any{it.title=="SalaryTimer provider test" && it.intersects(LocalDate.now())})
        } finally {
            if(inserted!=null)ctx.contentResolver.delete(inserted,null,null)
            automation.dropShellPermissionIdentity()
        }
    }
}
