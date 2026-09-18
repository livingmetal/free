package com.livingmetal.salarytimer

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SalaryApp() }
    }
}
private val Light = lightColorScheme(
    primary=Color(0xFF007D68),onPrimary=Color.White,primaryContainer=Color(0xFFDAF5EC),onPrimaryContainer=Color(0xFF064C40),
    secondary=Color(0xFF3E6860),background=Color(0xFFF4F7F6),surface=Color.White,onBackground=Color(0xFF182B2A),onSurface=Color(0xFF182B2A),
    surfaceVariant=Color(0xFFE6EFEB),onSurfaceVariant=Color(0xFF536964),outline=Color(0xFF748A83))
private val Dark = darkColorScheme(
    primary=Color(0xFF69DCB9),onPrimary=Color(0xFF00382C),primaryContainer=Color(0xFF173E34),onPrimaryContainer=Color(0xFFAAEED8),
    secondary=Color(0xFFA2CDC0),background=Color(0xFF0D1716),surface=Color(0xFF172320),onBackground=Color(0xFFE5F1EB),onSurface=Color(0xFFE5F1EB),
    surfaceVariant=Color(0xFF293B34),onSurfaceVariant=Color(0xFFB5C8BE),outline=Color(0xFF7F958A))

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SalaryApp() {
    val ctx=LocalContext.current
    val activity=ctx as ComponentActivity
    val store=remember { SalaryStore(ctx) }
    var data by remember { mutableStateOf(store.load()) }
    val update: (SalaryData)->Unit = { value -> store.save(value);data=value }
    var tab by rememberSaveable { mutableStateOf("timer") }
    var monthly by rememberSaveable { mutableStateOf(true) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var calMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedYear by rememberSaveable { mutableIntStateOf(LocalDate.now().year) }
    var workEditor by remember { mutableStateOf<WorkEntry?>(null) }
    var bonusEditor by remember { mutableStateOf<Bonus?>(null) }
    var explainCalendar by remember { mutableStateOf(false) }
    var phoneGranted by remember { mutableStateOf(PhoneCalendar.permitted(ctx)) }
    var calendarRefresh by remember { mutableIntStateOf(0) }
    var events by remember { mutableStateOf<List<PhoneEvent>>(emptyList()) }
    var eventError by remember { mutableStateOf<String?>(null) }
    var calendarBusy by remember { mutableStateOf(false) }
    val dark=when(data.config.theme){"dark"->true;"light"->false;else->isSystemInDarkTheme()}
    val notice: (String)->Unit = { Toast.makeText(ctx,it,Toast.LENGTH_LONG).show() }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){ granted->
        phoneGranted=granted
        update(data.copy(config=data.config.copy(calendarEnabled=granted)))
        if(!granted)notice("캘린더 권한 없이도 근무 달력과 급여 계산은 사용할 수 있습니다.")
    }
    val enableCalendar: (Boolean)->Unit = { enabled ->
        if(!enabled) { update(data.copy(config=data.config.copy(calendarEnabled=false)));events=emptyList() }
        else if(PhoneCalendar.permitted(ctx)){phoneGranted=true;update(data.copy(config=data.config.copy(calendarEnabled=true)));calendarRefresh++}
        else explainCalendar=true
    }
    LaunchedEffect(activity) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true){now=LocalDateTime.now();delay(1000)}
        }
    }
    DisposableEffect(activity) {
        val observer=LifecycleEventObserver { _,event->
            if(event==Lifecycle.Event.ON_RESUME){phoneGranted=PhoneCalendar.permitted(ctx);calendarRefresh++}
        }
        activity.lifecycle.addObserver(observer)
        onDispose{activity.lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(tab,calMonth,data.config.calendarEnabled,phoneGranted,calendarRefresh) {
        if(!data.config.calendarEnabled || !phoneGranted){events=emptyList();calendarBusy=false;eventError=null}
        else if(tab=="calendar"){
            calendarBusy=true;eventError=null;events=emptyList()
            val result=withContext(Dispatchers.IO){runCatching{PhoneCalendar.read(ctx,calMonth)}}
            events=result.getOrDefault(emptyList());calendarBusy=false
            if(result.isFailure)eventError="일정을 읽지 못했습니다. 캘린더 권한과 휴대폰 동기화 상태를 확인하세요."
        }
    }
    BackHandler(tab!="timer" && workEditor==null && bonusEditor==null){tab="timer"}
    MaterialTheme(colorScheme=if(dark)Dark else Light) {
        SideEffect {
            WindowCompat.getInsetsController(activity.window,activity.window.decorView).apply{
                isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark
            }
        }
        Scaffold(
            modifier=Modifier.fillMaxSize().testTag(if(dark)"app_dark" else "app_light"),
            containerColor=MaterialTheme.colorScheme.background,
            topBar={TopAppBar(title={Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Image(painterResource(R.drawable.launcher_art),null,Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)))
                Text("월급 타이머")
            }},actions={TextButton(onClick={update(data.copy(config=data.config.copy(theme=if(dark)"light" else "dark")))},modifier=Modifier.testTag("quick_theme")){Text(if(dark)"라이트" else "다크")}},colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))},
            bottomBar={NavigationBar(containerColor=MaterialTheme.colorScheme.surface){
                listOf("timer" to "타이머","calendar" to "달력","year" to "연간","settings" to "설정").forEach{(id,label)->
                    NavigationBarItem(selected=tab==id,onClick={tab=id},modifier=Modifier.testTag("nav_$id"),
                        icon={Icon(when(id){"calendar"->Icons.Default.DateRange;"year"->Icons.Default.List;"settings"->Icons.Default.Settings;else->Icons.Default.Home},null)},label={Text(label)})
                }
            }}
        ){ padding ->
            Box(Modifier.padding(padding).fillMaxSize()){
                when(tab){
                    "timer"->TimerScreen(data,now,monthly,{monthly=it},
                        {workEditor=data.work[now.toLocalDate()]?:WorkEntry(now.toLocalDate())},
                        {bonusEditor=Bonus(UUID.randomUUID().toString(),now.toLocalDate(),0,"",true)})
                    "calendar"->CalendarScreen(data,now,calMonth,selectedDate,events,calendarBusy,eventError,phoneGranted,
                        {calMonth=it;selectedDate=it.atDay(selectedDate.dayOfMonth.coerceAtMost(it.lengthOfMonth()))},
                        {selectedDate=it},
                        {workEditor=data.work[selectedDate]?:WorkEntry(selectedDate)},
                        {bonusEditor=Bonus(UUID.randomUUID().toString(),selectedDate,0,"",selectedDate<=now.toLocalDate())},
                        {bonusEditor=it},enableCalendar,{calendarRefresh++},
                        {if(!PhoneCalendar.view(ctx,it))notice("이 일정을 열 수 있는 캘린더 앱을 찾지 못했습니다.")},
                        {if(!PhoneCalendar.add(ctx,selectedDate,data.work[selectedDate]?.extraHours?:0.0))notice("휴대폰에 일정 입력을 지원하는 캘린더 앱이 필요합니다.")})
                    "year"->YearScreen(data,now,selectedYear,{selectedYear=it},
                        {m->calMonth=m;selectedDate=m.atDay(1);tab="calendar"},
                        {bonusEditor=Bonus(UUID.randomUUID().toString(),now.toLocalDate().withYear(selectedYear),0,"",selectedYear<=now.year)},
                        {bonusEditor=it})
                    else->SettingsScreen(data.config,{cfg->update(data.copy(config=cfg));notice("설정을 저장했습니다.")},
                        {theme->update(data.copy(config=data.config.copy(theme=theme)))},enableCalendar,phoneGranted)
                }
            }
        }
        workEditor?.let { entry -> WorkDialog(entry,dark,
            onSave={e->update(data.copy(work=data.work+(e.date to e)));workEditor=null},
            onDelete={update(data.copy(work=data.work-entry.date));workEditor=null},
            onDismiss={workEditor=null}) }
        bonusEditor?.let { bonus -> BonusDialog(bonus,dark,data.bonuses.any{it.id==bonus.id},
            onSave={b->update(data.copy(bonuses=data.bonuses.filterNot{it.id==b.id}+b));bonusEditor=null},
            onDelete={update(data.copy(bonuses=data.bonuses.filterNot{it.id==bonus.id}));bonusEditor=null},
            onDismiss={bonusEditor=null}) }
        if(explainCalendar)AlertDialog(onDismissRequest={explainCalendar=false},title={Text("휴대폰 캘린더 연결")},
            text={Text("휴대폰에 동기화된 캘린더의 일정 제목과 시간을 읽어 근무 달력에 표시합니다. 일정은 저장하거나 외부로 전송하지 않으며 급여 계산에 자동 반영하지 않습니다. 새 일정은 캘린더 앱에서 직접 확인 후 저장합니다.")},
            confirmButton={TextButton(onClick={explainCalendar=false;permission.launch(Manifest.permission.READ_CALENDAR)}){Text("읽기 권한 요청")}},
            dismissButton={TextButton(onClick={explainCalendar=false}){Text("사용하지 않음")}})
    }
}
