package com.livingmetal.salarytimer

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SalaryEngineTest {
    private val d=SalaryData(SalaryConfig(annual=52_800_000))
    private val m=YearMonth.of(2026,9)
    private val now=LocalDateTime.of(2026,9,18,14,30)
    @Test fun monthUsesExactWeekdayCount(){assertEquals(22,SalaryEngine.scheduledDays(d,m))}
    @Test fun lunchFreezesProgress(){assertEquals(SalaryEngine.elapsed(d,now.toLocalDate(),now.withHour(12).withMinute(1)),SalaryEngine.elapsed(d,now.toLocalDate(),now.withHour(12).withMinute(59)),0.001)}
    @Test fun beforeWorkIsZero(){assertEquals(0.0,SalaryEngine.elapsed(d,now.toLocalDate(),now.withHour(8)),0.001)}
    @Test fun afterWorkIsCapped(){assertEquals(28800.0,SalaryEngine.elapsed(d,now.toLocalDate(),now.withHour(23)),0.001)}
    @Test fun weekendBaseIsZero(){val t=LocalDateTime.of(2026,9,19,14,0);assertEquals(0.0,SalaryEngine.day(d,t.toLocalDate(),t).baseEarned,0.001)}
    @Test fun allDailyBaseSumsToMonthly(){val sum=SalaryEngine.monthDays(m).sumOf{SalaryEngine.day(d,it,now).baseEarned};assertEquals(sum,SalaryEngine.month(d,m,now).baseEarned,0.001)}
    @Test fun fullMonthEqualsSalaryDividedBy12(){assertEquals(d.config.annual/12.0,SalaryEngine.month(d,m,now.plusMonths(1)).baseEarned,0.001)}
    @Test fun futureMonthDoesNotAccrue(){assertEquals(0.0,SalaryEngine.month(d,m.plusMonths(1),now).earned,0.001)}
    @Test fun holidayExclusionPreservesMonthlySalary(){val date=LocalDate.of(2026,9,18);val x=d.copy(work=mapOf(date to WorkEntry(date,rest=true)));assertEquals(21,SalaryEngine.scheduledDays(x,m));assertEquals(0.0,SalaryEngine.day(x,date,now).baseEarned,0.001);assertEquals(d.config.annual/12.0,SalaryEngine.month(x,m,now.plusMonths(1)).baseEarned,0.001)}
    @Test fun zeroScheduledDaysCannotDivideByZero(){val x=d.copy(work=SalaryEngine.monthDays(m).associateWith{WorkEntry(it,rest=true)});val t=SalaryEngine.month(x,m,now);assertTrue(t.earned.isFinite());assertTrue(t.earned>0);assertEquals(t.baseEarned,SalaryEngine.monthDays(m).sumOf{SalaryEngine.day(x,it,now).baseEarned},0.001)}
    @Test fun weekendHoursAndMultiplier(){val date=LocalDate.of(2026,9,12);val x=d.copy(config=d.config.copy(extraHourly=20_000),work=mapOf(date to WorkEntry(date,extraHours=4.5,multiplier=1.5)));assertEquals(135000.0,SalaryEngine.month(x,m,now).extraEarned,0.001)}
    @Test fun futureExtraIsOnlyPlanned(){val date=LocalDate.of(2026,9,19);val x=d.copy(work=mapOf(date to WorkEntry(date,extraHours=5.0)));val t=SalaryEngine.month(x,m,now);assertEquals(0.0,t.extraEarned,0.001);assertTrue(t.extraPlanned>0)}
    @Test fun paidAndPendingBonusesAreSeparated(){val x=d.copy(bonuses=listOf(Bonus("1",now.toLocalDate(),500_000,paid=true),Bonus("2",now.toLocalDate(),200_000,paid=false),Bonus("3",now.toLocalDate().plusDays(5),300_000,paid=true)));val t=SalaryEngine.month(x,m,now);assertEquals(500000.0,t.bonusEarned,0.001);assertEquals(1000000.0,t.bonusPlanned,0.001)}
    @Test fun yearIsSumOf12Months(){val t=SalaryEngine.year(d,2026,now);assertEquals((1..12).sumOf{SalaryEngine.month(d,YearMonth.of(2026,it),now).earned},t.earned,0.001);assertEquals(d.config.annual.toDouble(),t.basePlanned,0.001)}
    @Test fun leapDayAndPaydayClamp(){assertEquals(LocalDate.of(2028,2,29),SalaryEngine.payday(YearMonth.of(2028,2),31));assertEquals(LocalDate.of(2027,2,28),SalaryEngine.payday(YearMonth.of(2027,2),31));assertEquals(LocalDate.of(2026,10,25),SalaryEngine.nextPayday(LocalDate.of(2026,9,26),25))}
    @Test fun invalidHoursAndNaNRejected(){assertNotNull(SalaryEngine.validate(WorkEntry(now.toLocalDate(),extraHours=Double.NaN)));assertNotNull(SalaryEngine.validate(WorkEntry(now.toLocalDate(),extraHours=25.0)));assertNotNull(SalaryEngine.validate(WorkEntry(now.toLocalDate(),multiplier=-1.0)))}
    @Test fun invalidSettingsRejected(){assertNotNull(SalaryEngine.validate(d.config.copy(annual=0)));assertNotNull(SalaryEngine.validate(d.config.copy(end="08:00")));assertNotNull(SalaryEngine.validate(d.config.copy(breakStart="07:00")));assertNull(SalaryEngine.validate(d.config))}
    @Test fun allDayCalendarUsesUtcDates(){val b=LocalDate.of(2026,9,18).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();val e=PhoneEvent(1,"test",b,b+86400000,true);assertTrue(e.intersects(LocalDate.of(2026,9,18),ZoneId.of("America/Los_Angeles")));assertFalse(e.intersects(LocalDate.of(2026,9,17),ZoneId.of("America/Los_Angeles")));assertFalse(e.intersects(LocalDate.of(2026,9,19),ZoneId.of("Asia/Seoul")))}
}
