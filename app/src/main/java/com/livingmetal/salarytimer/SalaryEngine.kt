package com.livingmetal.salarytimer

import java.time.*

data class SalaryConfig(
    val annual: Long = 60_000_000,
    val payday: Int = 25,
    val start: String = "09:00",
    val end: String = "18:00",
    val breakStart: String = "12:00",
    val breakMinutes: Int = 60,
    val theme: String = "system",
    val calendarEnabled: Boolean = false,
    val extraHourly: Long = 0
)
data class WorkEntry(val date: LocalDate, val rest: Boolean = false, val extraHours: Double = 0.0, val multiplier: Double = 1.0, val note: String = "")
data class Bonus(val id: String, val date: LocalDate, val amount: Long, val title: String = "보너스", val paid: Boolean = false)
data class SalaryData(val config: SalaryConfig = SalaryConfig(), val work: Map<LocalDate, WorkEntry> = emptyMap(), val bonuses: List<Bonus> = emptyList())
data class Totals(val baseEarned: Double, val basePlanned: Double, val extraEarned: Double, val extraPlanned: Double, val bonusEarned: Double, val bonusPlanned: Double, val extraHours: Double, val plannedWorkDays: Int) {
    val earned get() = baseEarned + extraEarned + bonusEarned
    val planned get() = basePlanned + extraPlanned + bonusPlanned
    val remaining get() = (planned - earned).coerceAtLeast(0.0)
    val progress get() = if(planned > 0) (earned / planned).toFloat().coerceIn(0f,1f) else 0f
    operator fun plus(o: Totals) = Totals(baseEarned+o.baseEarned,basePlanned+o.basePlanned,extraEarned+o.extraEarned,extraPlanned+o.extraPlanned,bonusEarned+o.bonusEarned,bonusPlanned+o.bonusPlanned,extraHours+o.extraHours,plannedWorkDays+o.plannedWorkDays)
    companion object { val ZERO = Totals(0.0,0.0,0.0,0.0,0.0,0.0,0.0,0) }
}

/** Display estimates only, not a payroll, tax, or statutory overtime calculation. */
object SalaryEngine {
    fun validate(c: SalaryConfig): String? {
        if(c.annual <= 0 || c.annual > 1_000_000_000_000L) return "연봉은 1원 이상 1조원 이하로 입력하세요."
        if(c.payday !in 1..31) return "급여일은 1~31일입니다."
        val st = runCatching { LocalTime.parse(c.start) }.getOrNull() ?: return "출근 시간을 HH:mm 형식으로 입력하세요."
        val en = runCatching { LocalTime.parse(c.end) }.getOrNull() ?: return "퇴근 시간을 HH:mm 형식으로 입력하세요."
        val bs = runCatching { LocalTime.parse(c.breakStart) }.getOrNull() ?: return "휴게 시작을 HH:mm 형식으로 입력하세요."
        if(en <= st) return "퇴근은 출근 이후여야 합니다. 자정을 넘는 교대근무는 아직 지원하지 않습니다."
        if(c.breakMinutes !in 0..720) return "휴게시간은 0~720분으로 입력하세요."
        if(c.breakMinutes > 0 && (bs < st || bs.toSecondOfDay() + c.breakMinutes*60 > en.toSecondOfDay())) return "휴게시간은 출퇴근 시간 안에 있어야 합니다."
        if(Duration.between(st,en).seconds <= c.breakMinutes*60) return "실근무시간이 0보다 커야 합니다."
        if(c.extraHourly < 0 || c.extraHourly > 1_000_000_000L) return "추가근무 시급을 확인하세요."
        return null
    }
    fun validate(e: WorkEntry): String? = when {
        !e.extraHours.isFinite() || e.extraHours !in 0.0..24.0 -> "추가 시수는 0~24시간입니다."
        !e.multiplier.isFinite() || e.multiplier !in 0.0..10.0 -> "배율은 0~10 사이로 입력하세요."
        e.note.length > 120 -> "메모는 120자까지 입력하세요."
        else -> null
    }
    fun workSeconds(c: SalaryConfig): Double = (Duration.between(LocalTime.parse(c.start),LocalTime.parse(c.end)).seconds-c.breakMinutes*60).toDouble()
    fun isWorkday(data: SalaryData,d: LocalDate) = d.dayOfWeek.value <= 5 && data.work[d]?.rest != true
    fun monthDays(m: YearMonth): List<LocalDate> = (1..m.lengthOfMonth()).map { m.atDay(it) }
    fun scheduledDays(data: SalaryData,m: YearMonth) = monthDays(m).count { isWorkday(data,it) }
    fun elapsed(data: SalaryData,d: LocalDate,now: LocalDateTime): Double {
        if(!isWorkday(data,d) || d > now.toLocalDate()) return 0.0
        val c=data.config; val total=workSeconds(c)
        if(d < now.toLocalDate()) return total
        val start=LocalTime.parse(c.start).toSecondOfDay().toDouble()
        val end=LocalTime.parse(c.end).toSecondOfDay().toDouble()
        val t=(now.toLocalTime().toSecondOfDay()+now.nano/1e9).coerceIn(start,end)
        val bs=LocalTime.parse(c.breakStart).toSecondOfDay().toDouble()
        val breakElapsed=if(c.breakMinutes>0)(t-bs).coerceIn(0.0,c.breakMinutes*60.0) else 0.0
        return (t-start-breakElapsed).coerceIn(0.0,total)
    }
    fun hourly(data: SalaryData,m: YearMonth): Double {
        val seconds=scheduledDays(data,m)*workSeconds(data.config)
        return if(seconds>0)data.config.annual/12.0/seconds*3600.0 else 0.0
    }
    fun extraAmount(data: SalaryData,e: WorkEntry): Double {
        val rate=if(data.config.extraHourly>0)data.config.extraHourly.toDouble() else hourly(data,YearMonth.from(e.date))
        return e.extraHours*e.multiplier*rate
    }
    private fun monthBaseProgress(data: SalaryData,m: YearMonth,now: LocalDateTime): Double {
        if(m < YearMonth.from(now)) return 1.0
        if(m > YearMonth.from(now)) return 0.0
        val denominator=scheduledDays(data,m)*workSeconds(data.config)
        if(denominator==0.0)return ((now.dayOfMonth-1)+(now.toLocalTime().toSecondOfDay()/86400.0))/m.lengthOfMonth()
        return (monthDays(m).sumOf { elapsed(data,it,now) }/denominator).coerceIn(0.0,1.0)
    }
    fun month(data: SalaryData,m: YearMonth,now: LocalDateTime): Totals {
        val entries=data.work.values.filter { YearMonth.from(it.date)==m }
        val bonuses=data.bonuses.filter { YearMonth.from(it.date)==m }
        val base=data.config.annual/12.0
        return Totals(base*monthBaseProgress(data,m,now),base,
            entries.filter{it.date<=now.toLocalDate()}.sumOf{extraAmount(data,it)},entries.sumOf{extraAmount(data,it)},
            bonuses.filter{it.paid && it.date<=now.toLocalDate()}.sumOf{it.amount.toDouble()},bonuses.sumOf{it.amount.toDouble()},
            entries.sumOf{it.extraHours},scheduledDays(data,m))
    }
    fun day(data: SalaryData,date: LocalDate,now: LocalDateTime): Totals {
        val m=YearMonth.from(date);val days=scheduledDays(data,m);val base=data.config.annual/12.0
        val planned=if(days==0)base/m.lengthOfMonth() else if(isWorkday(data,date))base/days else 0.0
        val progress=if(days==0)when{date<now.toLocalDate()->1.0;date>now.toLocalDate()->0.0;else->now.toLocalTime().toSecondOfDay()/86400.0} else elapsed(data,date,now)/workSeconds(data.config)
        val e=data.work[date];val extra=if(e==null)0.0 else extraAmount(data,e)
        val bonuses=data.bonuses.filter{it.date==date}
        return Totals(planned*progress,planned,if(date<=now.toLocalDate())extra else 0.0,extra,
            bonuses.filter{it.paid && date<=now.toLocalDate()}.sumOf{it.amount.toDouble()},bonuses.sumOf{it.amount.toDouble()},e?.extraHours?:0.0,if(isWorkday(data,date))1 else 0)
    }
    fun year(data: SalaryData,year: Int,now: LocalDateTime) = (1..12).map{month(data,YearMonth.of(year,it),now)}.fold(Totals.ZERO){a,b->a+b}
    fun payday(m: YearMonth,requested: Int)=m.atDay(requested.coerceIn(1,m.lengthOfMonth()))
    fun nextPayday(today: LocalDate,requested: Int): LocalDate {
        val p=payday(YearMonth.from(today),requested)
        return if(p<today)payday(YearMonth.from(today).plusMonths(1),requested) else p
    }
    fun status(data: SalaryData,now: LocalDateTime): String {
        if(!isWorkday(data,now.toLocalDate()))return "오늘은 쉬어가는 날"
        val c=data.config;val t=now.toLocalTime()
        if(t<LocalTime.parse(c.start))return "출근 전 · 오늘의 시간을 준비해요"
        if(t>=LocalTime.parse(c.end))return "오늘의 근무 완료"
        val bs=LocalTime.parse(c.breakStart)
        if(c.breakMinutes>0 && t>=bs && t<bs.plusMinutes(c.breakMinutes.toLong()))return "휴게시간 · 잠시 쉬어가요"
        return "근무 중 · 시간이 급여로 쌓이고 있어요"
    }
}
