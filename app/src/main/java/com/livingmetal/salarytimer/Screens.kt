package com.livingmetal.salarytimer

import android.app.DatePickerDialog
import android.view.ContextThemeWrapper
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private fun won(value: Double)="₩ "+NumberFormat.getIntegerInstance(Locale.KOREA).format(value.toLong())
private fun num(value: Double)=String.format(Locale.KOREA,"%.1f",value)
private val koreanDate=DateTimeFormatter.ofPattern("M월 d일 EEEE",Locale.KOREAN)
@Composable private fun Muted(text: String,modifier: Modifier=Modifier){Text(text,modifier,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
@Composable private fun Panel(modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit){
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)}
}
@Composable private fun SectionTitle(title: String,action: String?=null,onClick: ()->Unit={}){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold);if(action!=null)TextButton(onClick=onClick){Text(action)}}
}
@Composable private fun ValueLine(label: String,value: String,tag: String=""){
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium);Text(value,Modifier.testTag(tag),fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.bodyMedium)}
}
@Composable private fun MoneySummary(title: String,t: Totals,subtitle: String,tag: String){
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.primaryContainer){
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            Text(title,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onPrimaryContainer)
            Text(won(t.earned),Modifier.testTag(tag),fontSize=if(t.earned>=1e10)28.sp else 36.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onPrimaryContainer)
            LinearProgressIndicator(progress={t.progress},Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),color=MaterialTheme.colorScheme.primary,trackColor=MaterialTheme.colorScheme.surface.copy(alpha=.5f))
            Text("목표 대비 ${num(t.progress*100.0)}%",color=MaterialTheme.colorScheme.onPrimaryContainer)
            Muted(subtitle)
        }
    }
}
@Composable fun TimerScreen(data: SalaryData,now: LocalDateTime,monthly: Boolean,setMonthly:(Boolean)->Unit,onWork:()->Unit,onBonus:()->Unit){
    val today=now.toLocalDate();val month=YearMonth.from(today)
    val totals=if(monthly)SalaryEngine.month(data,month,now) else SalaryEngine.day(data,today,now)
    val c=data.config;val pay=SalaryEngine.nextPayday(today,c.payday);val dday=ChronoUnit.DAYS.between(today,pay)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("timer_screen"),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            FilterChip(selected=!monthly,onClick={setMonthly(false)},label={Text("오늘")},modifier=Modifier.weight(1f).testTag("mode_today"))
            FilterChip(selected=monthly,onClick={setMonthly(true)},label={Text("이번 달")},modifier=Modifier.weight(1f).testTag("mode_month"))
        }
        Text(if(monthly)"${now.year}년 ${now.monthValue}월" else today.format(koreanDate),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        MoneySummary(if(monthly)"이번 달 누적 환산액" else "오늘 누적 환산액",totals,SalaryEngine.status(data,now),"main_amount")
        Panel {
            ValueLine("기본급 누적",won(totals.baseEarned))
            ValueLine("추가근무 누적",won(totals.extraEarned))
            ValueLine("지급 보너스",won(totals.bonusEarned))
            HorizontalDivider()
            ValueLine(if(monthly)"이번 달 예정 합계" else "오늘 예정 합계",won(totals.planned))
            ValueLine("예정 합계까지 남은 금액",won(totals.remaining))
        }
        Panel {
            ValueLine("다음 급여일",if(dday==0L)"오늘!" else "D-$dday · ${pay.monthValue}/${pay.dayOfMonth}")
            ValueLine("이번 달 기본급 환산 시급",won(SalaryEngine.hourly(data,month)))
            ValueLine("기본급 환산 초당",won(SalaryEngine.hourly(data,month)/3600.0))
            if(SalaryEngine.isWorkday(data,today)){
                val seconds=Duration.between(now,today.atTime(LocalTime.parse(c.end))).seconds.coerceAtLeast(0)
                ValueLine("퇴근까지",if(seconds==0L)"근무 완료" else "${seconds/3600}시간 ${(seconds%3600)/60}분")
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedButton(onClick=onWork,modifier=Modifier.weight(1f).testTag("quick_work")){Text("추가 시수")}
            OutlinedButton(onClick=onBonus,modifier=Modifier.weight(1f).testTag("quick_bonus")){Text("보너스 입력")}
        }
        Muted("시간은 급여로, 기록은 내 편으로.")
        Muted("입금 잔액이 아닌 참고용 환산액입니다. 기본급은 매월 1일~말일 기준이며 급여일은 D-Day 표시용입니다.")
        Spacer(Modifier.height(12.dp))
    }
}

@Composable fun CalendarScreen(data: SalaryData,now: LocalDateTime,month: YearMonth,selected: LocalDate,events: List<PhoneEvent>,busy: Boolean,error: String?,granted: Boolean,onMonth:(YearMonth)->Unit,onDate:(LocalDate)->Unit,onWork:()->Unit,onNewBonus:()->Unit,onEditBonus:(Bonus)->Unit,onConnect:(Boolean)->Unit,onRefresh:()->Unit,onEvent:(PhoneEvent)->Unit,onAddEvent:()->Unit){
    val entry=data.work[selected];val dayBonuses=data.bonuses.filter{it.date==selected};val dayEvents=events.filter{it.intersects(selected)}
    val pay=SalaryEngine.payday(month,data.config.payday)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("calendar_screen"),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
            TextButton(onClick={onMonth(month.minusMonths(1))},enabled=month.year>1970,modifier=Modifier.testTag("prev_month")){Text("‹ 이전")}
            Text("${month.year}년 ${month.monthValue}월",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
            TextButton(onClick={onMonth(month.plusMonths(1))},enabled=month.year<2200,modifier=Modifier.testTag("next_month")){Text("다음 ›")}
        }
        Panel {
            Row { listOf("일","월","화","수","목","금","토").forEach{Text(it,Modifier.weight(1f),textAlign=TextAlign.Center,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)} }
            val offset=month.atDay(1).dayOfWeek.value%7
            val cellCount=((offset+month.lengthOfMonth()+6)/7)*7
            for(row in 0 until cellCount/7){
                Row(Modifier.fillMaxWidth()){
                    for(col in 0..6){
                        val day=row*7+col-offset+1
                        if(day !in 1..month.lengthOfMonth())Spacer(Modifier.weight(1f).height(57.dp))
                        else {
                            val date=month.atDay(day);val work=data.work[date];val bonus=data.bonuses.any{it.date==date};val hasEvent=events.any{it.intersects(date)}
                            val active=date==selected
                            val label=when{work!=null && work.extraHours>0->"+시수";bonus->"보너스";date==pay->"급여";work?.rest==true->"휴무";hasEvent->"일정";else->""}
                            Column(Modifier.weight(1f).height(57.dp).padding(1.dp).clip(RoundedCornerShape(12.dp))
                                .background(if(active)MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .border(if(date==now.toLocalDate())1.dp else 0.dp,if(date==now.toLocalDate())MaterialTheme.colorScheme.primary else Color.Transparent,RoundedCornerShape(12.dp))
                                .clickable{onDate(date)}.testTag("day_$date"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
                                Text(day.toString(),fontWeight=if(active)FontWeight.Bold else FontWeight.Normal,color=when{active->MaterialTheme.colorScheme.primary;col==0->MaterialTheme.colorScheme.error;else->MaterialTheme.colorScheme.onSurface})
                                Text(label,fontSize=9.sp,color=MaterialTheme.colorScheme.primary,maxLines=1)
                            }
                        }
                    }
                }
            }
            Muted("급여 · 보너스 · +시수 · 휴무 · 휴대폰 일정")
        }
        Panel {
            Text(selected.format(koreanDate),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Text(if(SalaryEngine.isWorkday(data,selected))"기본 근무일" else "기본근무 없는 날",color=MaterialTheme.colorScheme.onSurfaceVariant)
            ValueLine("기록한 추가근무","${num(entry?.extraHours?:0.0)}시간 × ${num(entry?.multiplier?:1.0)}배", "calendar_hours")
            ValueLine("추가근무 환산액",won(if(entry==null)0.0 else SalaryEngine.extraAmount(data,entry)))
            if(!entry?.note.isNullOrBlank())Text(entry!!.note)
            Button(onClick=onWork,modifier=Modifier.fillMaxWidth().testTag("edit_work")){Text("근무 / 주말 시수 입력")}
            OutlinedButton(onClick=onNewBonus,modifier=Modifier.fillMaxWidth().testTag("add_bonus_calendar")){Text("이 날짜에 보너스 입력")}
            dayBonuses.forEach { b-> BonusRow(b){onEditBonus(b)} }
        }
        Panel {
            SectionTitle("휴대폰 캘린더",if(data.config.calendarEnabled && granted)"새로고침" else "연결",{if(data.config.calendarEnabled && granted)onRefresh() else onConnect(true)})
            if(!data.config.calendarEnabled || !granted)Muted("설정에서 읽기 권한을 허용하면 휴대폰에 동기화된 일정이 여기 표시됩니다. 연결 없이도 근무 달력은 사용할 수 있습니다.")
            else if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            else if(error!=null)Text(error,color=MaterialTheme.colorScheme.error)
            else if(dayEvents.isEmpty())Muted("이 날짜에 표시할 휴대폰 일정이 없습니다.")
            else dayEvents.forEach{e->Row(Modifier.fillMaxWidth().clickable{onEvent(e)}.padding(vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(e.timeLabel(),color=MaterialTheme.colorScheme.primary);Text(e.title,Modifier.weight(1f))}}
            OutlinedButton(onClick=onAddEvent,modifier=Modifier.fillMaxWidth()){Text("휴대폰 달력에 일정 추가")}
            Muted("일정 추가는 캘린더 앱에서 확인 후 저장합니다. 연봉·금액은 보내지 않습니다. 일정이나 공휴일은 급여 계산에 자동 적용되지 않습니다.")
        }
        Muted("공휴일·연차는 날짜를 눌러 휴무로 지정하세요. 주말 시수는 추가근무로 별도 집계됩니다.")
        Spacer(Modifier.height(12.dp))
    }
}

@Composable fun YearScreen(data: SalaryData,now: LocalDateTime,year: Int,onYear:(Int)->Unit,onMonth:(YearMonth)->Unit,onNewBonus:()->Unit,onEditBonus:(Bonus)->Unit){
    val total=SalaryEngine.year(data,year,now)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("year_screen"),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
            TextButton(onClick={onYear(year-1)},enabled=year>1970){Text("‹ 이전")}
            Text("${year}년",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            TextButton(onClick={onYear(year+1)},enabled=year<2200){Text("다음 ›")}
        }
        MoneySummary("연간 누적 환산액",total,"현재 연봉·근무조건으로 계산한 추정치입니다.","year_total")
        Panel {
            ValueLine("연간 기본급",won(total.basePlanned))
            ValueLine("추가근무 예정 합계",won(total.extraPlanned))
            ValueLine("보너스 예정 합계",won(total.bonusPlanned))
            ValueLine("입력한 추가 시수","${num(total.extraHours)}시간")
            HorizontalDivider();ValueLine("연간 예정 합계",won(total.planned))
        }
        Panel {
            SectionTitle("월별 보기")
            for(m in 1..12){
                val ym=YearMonth.of(year,m);val t=SalaryEngine.month(data,ym,now)
                Column(Modifier.fillMaxWidth().clickable{onMonth(ym)}.padding(vertical=8.dp).testTag("year_month_$m"),verticalArrangement=Arrangement.spacedBy(5.dp)){
                    ValueLine("${m}월",won(t.earned))
                    LinearProgressIndicator(progress={t.progress},modifier=Modifier.fillMaxWidth().height(4.dp))
                    Muted("기본 ${won(t.baseEarned)}  ·  추가 ${won(t.extraEarned)}  ·  보너스 ${won(t.bonusEarned)}")
                }
                if(m<12)HorizontalDivider()
            }
        }
        Panel {
            SectionTitle("보너스 내역","추가",onNewBonus)
            val bonuses=data.bonuses.filter{it.date.year==year}.sortedByDescending{it.date}
            if(bonuses.isEmpty())Muted("아직 기록한 보너스가 없습니다.")
            bonuses.forEach{b->BonusRow(b){onEditBonus(b)}}
        }
        Muted("연봉은 별도 보너스를 제외한 금액으로 입력하세요. 설정 변경 시 과거 환산액도 다시 계산되며, 실제 급여명세 이력은 아닙니다.")
        Spacer(Modifier.height(12.dp))
    }
}
@Composable private fun BonusRow(b: Bonus,onClick:()->Unit){
    Column(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
        ValueLine(b.title,won(b.amount.toDouble()))
        Muted("${b.date} · ${if(b.paid)"지급 완료" else "지급 예정"}")
    }
}
@Composable fun SettingsScreen(c: SalaryConfig,onSave:(SalaryConfig)->Unit,onTheme:(String)->Unit,onCalendar:(Boolean)->Unit,granted: Boolean){
    var annual by remember(c.annual){mutableStateOf(c.annual.toString())}
    var payday by remember(c.payday){mutableStateOf(c.payday.toString())}
    var start by remember(c.start){mutableStateOf(c.start)}
    var end by remember(c.end){mutableStateOf(c.end)}
    var bs by remember(c.breakStart){mutableStateOf(c.breakStart)}
    var minutes by remember(c.breakMinutes){mutableStateOf(c.breakMinutes.toString())}
    var hourly by remember(c.extraHourly){mutableStateOf(c.extraHourly.toString())}
    var error by remember{mutableStateOf<String?>(null)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("settings_screen"),verticalArrangement=Arrangement.spacedBy(16.dp)){
        Panel {
            SectionTitle("화면 테마")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                listOf("system" to "시스템","light" to "라이트","dark" to "다크").forEach{(key,label)->FilterChip(selected=c.theme==key,onClick={onTheme(key)},label={Text(label)},modifier=Modifier.testTag("theme_$key"))}
            }
        }
        Panel {
            SectionTitle("휴대폰 캘린더")
            Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("휴대폰 일정 표시");Muted(if(c.calendarEnabled && granted)"연결됨 · 읽기 전용" else "연결 안 됨")};Switch(checked=c.calendarEnabled && granted,onCheckedChange=onCalendar,modifier=Modifier.testTag("calendar_switch"))}
            Muted("읽기 허용 시 휴대폰에 동기화된 일정을 표시합니다. 이 앱은 일정이나 급여정보를 서버로 보내지 않습니다.")
        }
        Panel {
            SectionTitle("급여 / 근무 설정")
            Input("연봉 (원, 별도 보너스 제외)",annual,{annual=it.filter(Char::isDigit).take(13)},"annual_input",KeyboardType.Number)
            Muted("세전/세후 중 원하는 한 기준으로 금액을 통일하세요. 세금은 자동 계산하지 않습니다.")
            Input("급여일 (1~31)",payday,{payday=it.filter(Char::isDigit).take(2)},"payday_input",KeyboardType.Number)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Input("출근 HH:mm",start,{start=it},"start_input",modifier=Modifier.weight(1f))
                Input("퇴근 HH:mm",end,{end=it},"end_input",modifier=Modifier.weight(1f))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Input("휴게 시작",bs,{bs=it},"break_start",modifier=Modifier.weight(1f))
                Input("휴게 분",minutes,{minutes=it.filter(Char::isDigit).take(3)},"break_minutes",KeyboardType.Number,Modifier.weight(1f))
            }
            Input("추가근무 시급 (원)",hourly,{hourly=it.filter(Char::isDigit).take(10)},"extra_hourly",KeyboardType.Number)
            Muted("0원이면 해당 월의 기본급 환산 시급을 사용합니다. 실제 수당 시급을 알고 있다면 직접 입력하세요.")
            if(error!=null)Text(error!!,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("settings_error"))
            Button(onClick={
                val value=c.copy(annual=annual.toLongOrNull()?:0,payday=payday.toIntOrNull()?:0,start=start.trim(),end=end.trim(),breakStart=bs.trim(),breakMinutes=minutes.toIntOrNull()?:-1,extraHourly=hourly.toLongOrNull()?:-1)
                error=SalaryEngine.validate(value);if(error==null)onSave(value)
            },modifier=Modifier.fillMaxWidth().testTag("save_settings")){Text("급여 / 근무 설정 저장")}
        }
        Panel {
            SectionTitle("계산 기준")
            Muted("기본급 = 연봉 ÷ 12. 해당 월의 월~금 실근무시간에 나눠 배분하며 오늘·월간·연간 화면은 같은 기준입니다.")
            Muted("휴무로 지정한 평일은 기본급 배분시간에서 제외하지만 월 기본급은 줄이지 않습니다. 모든 근무일이 휴무이면 날짜 비율로 배분합니다.")
            Muted("추가근무 = 시수 × 지정 시급 × 입력 배율. 오늘·과거 날짜는 저장 즉시 누적, 미래 날짜는 예정 합계에만 반영합니다. 법정 가산율을 자동 판정하지 않습니다.")
            Muted("보너스는 입력한 날짜와 지급 완료 상태로 누적합니다. 연봉에 이미 포함된 보너스를 다시 넣으면 중복 계산됩니다.")
            Muted("공휴일 자동 제외, 세금, 급여일의 은행 휴일 보정, 야간 교대근무는 지원하지 않습니다. 참고용이며 실제 입금·급여명세와 다를 수 있습니다.")
        }
        Muted("월급 타이머 1.4.0 · 인터넷 권한 없음 · 기록은 기기 내부 저장")
        Spacer(Modifier.height(16.dp))
    }
}

@Composable internal fun Input(label: String,value: String,onChange:(String)->Unit,tag: String,type: KeyboardType=KeyboardType.Text,modifier: Modifier=Modifier){
    OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=type),modifier=modifier.fillMaxWidth().testTag(tag),shape=RoundedCornerShape(12.dp))
}
@Composable internal fun DateButton(date: LocalDate,dark: Boolean,onChange:(LocalDate)->Unit){
    val ctx=LocalContext.current
    OutlinedButton(onClick={val theme=if(dark)android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert
        DatePickerDialog(ContextThemeWrapper(ctx,theme),{_,y,m,d->onChange(LocalDate.of(y,m+1,d))},date.year,date.monthValue-1,date.dayOfMonth).apply{
            datePicker.minDate=LocalDate.of(1970,1,1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            datePicker.maxDate=LocalDate.of(2200,12,31).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.show()
    },modifier=Modifier.fillMaxWidth().testTag("date_picker")){Text(date.format(koreanDate)+" · ${date.year}")}
}
@Composable fun WorkDialog(initial: WorkEntry,dark: Boolean,onSave:(WorkEntry)->Unit,onDelete:()->Unit,onDismiss:()->Unit){
    var hours by remember(initial){mutableStateOf(if(initial.extraHours==0.0)"" else initial.extraHours.toString())}
    var multiplier by remember(initial){mutableStateOf(initial.multiplier.toString())}
    var rest by remember(initial){mutableStateOf(initial.rest)}
    var note by remember(initial){mutableStateOf(initial.note)}
    var error by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("근무 / 추가 시수")},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(initial.date.format(koreanDate))
            Row(verticalAlignment=Alignment.CenterVertically){Text("기본근무 제외 (휴무)",Modifier.weight(1f));Switch(checked=rest,onCheckedChange={rest=it},modifier=Modifier.testTag("rest_switch"))}
            Input("추가 시수 (시간, 예: 4.5)",hours,{hours=it},"work_hours",KeyboardType.Decimal)
            Input("수당 배율 (예: 1.0 또는 1.5)",multiplier,{multiplier=it},"work_multiplier",KeyboardType.Decimal)
            Input("메모",note,{note=it.take(120)},"work_note")
            Muted("주말도 입력 가능합니다. 추가 시수만 입력하세요. 오늘·과거는 즉시 누적, 미래는 예정액에 반영됩니다. 배율은 회사 기준에 맞게 직접 지정합니다.")
            if(error!=null)Text(error!!,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("work_error"))
            TextButton(onClick=onDelete){Text("이 날짜 기록 지우기")}
        }},confirmButton={TextButton(onClick={
            val h=if(hours.isBlank())0.0 else hours.replace(',','.').toDoubleOrNull()?:Double.NaN
            val e=initial.copy(rest=rest,extraHours=h,multiplier=multiplier.replace(',','.').toDoubleOrNull()?:Double.NaN,note=note)
            error=SalaryEngine.validate(e);if(error==null)onSave(e)
        },modifier=Modifier.testTag("save_work")){Text("저장")}},dismissButton={TextButton(onClick=onDismiss){Text("취소")}})
}
@Composable fun BonusDialog(initial: Bonus,dark: Boolean,existing: Boolean,onSave:(Bonus)->Unit,onDelete:()->Unit,onDismiss:()->Unit){
    var date by remember(initial){mutableStateOf(initial.date)}
    var title by remember(initial){mutableStateOf(initial.title)}
    var amount by remember(initial){mutableStateOf(if(initial.amount==0L)"" else initial.amount.toString())}
    var paid by remember(initial){mutableStateOf(initial.paid)}
    var error by remember{mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(existing)"보너스 수정" else "보너스 입력")},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){
            DateButton(date,dark){date=it;if(date>LocalDate.now())paid=false}
            Input("이름 (예: 성과급)",title,{title=it.take(60)},"bonus_title")
            Input("보너스 금액 (원)",amount,{amount=it.filter(Char::isDigit).take(13)},"bonus_amount",KeyboardType.Number)
            Row(verticalAlignment=Alignment.CenterVertically){Text("지급 완료",Modifier.weight(1f));Switch(checked=paid,onCheckedChange={paid=it},enabled=date<=LocalDate.now(),modifier=Modifier.testTag("bonus_paid"))}
            Muted("예정 보너스는 예정 합계에만 표시합니다. 지급일이 오늘·과거이고 지급 완료이면 누적에 반영합니다. 연봉에 이미 포함된 금액은 중복 입력하지 마세요.")
            if(error!=null)Text(error!!,color=MaterialTheme.colorScheme.error)
            if(existing)TextButton(onClick=onDelete){Text("보너스 삭제")}
        }},confirmButton={TextButton(onClick={val money=amount.toLongOrNull()
            error=when{money==null || money !in 1..1_000_000_000_000L->"보너스는 1원 이상 1조원 이하입니다.";paid && date>LocalDate.now()->"미래 날짜는 지급 예정으로 기록하세요.";else->null}
            if(error==null)onSave(initial.copy(date=date,amount=money!!,title=title.ifBlank{"보너스"},paid=paid))
        },modifier=Modifier.testTag("save_bonus")){Text("저장")}},dismissButton={TextButton(onClick=onDismiss){Text("취소")}})
}
