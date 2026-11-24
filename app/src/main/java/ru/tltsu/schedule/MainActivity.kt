package ru.tltsu.schedule

import android.Manifest
import android.app.*
import android.app.job.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.concurrent.Executors

private val zone = ZoneId.of("Europe/Samara")
private val fmt = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
private val ru = Locale("ru")
private val dayFmt = DateTimeFormatter.ofPattern("d MMMM, EEEE",ru)
private fun today() = LocalDate.now(zone)
private fun week(d:LocalDate) = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
private fun prefs(c:Context) = c.getSharedPreferences("schedule",0)
private val executor = Executors.newSingleThreadExecutor()
data class Lesson(val id:String,val name:String,val type:String,val number:Int,val date:LocalDate,val start:LocalTime,val end:LocalTime,val room:String,val groups:String,val note:String) {
    fun begins() = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
    fun ends() = date.atTime(end).atZone(zone).toInstant().toEpochMilli()
    fun time() = start.toString()+" – "+end.toString()
}
private fun parse(raw:String):List<Lesson> {
    val arr=JSONArray(raw)
    return (0 until arr.length()).map { i ->
        val x=arr.getJSONObject(i)
        val g=x.optJSONArray("groupsList") ?: JSONArray()
        val room=x.optJSONObject("classroom")
        Lesson(x.getString("id"),x.optString("disciplineName"),when(val t=x.optString("type")){"Лек"->"Лекция";"Лаб"->"Лабораторная";"ПрЗ"->"Практика";else->t},x.optInt("pairNumber"),LocalDate.parse(x.getString("date").replace('.','-')),LocalTime.parse(x.getString("fromTime").substring(11,16)),LocalTime.parse(x.getString("toTime").substring(11,16)),room?.optString("number").orEmpty().ifBlank{room?.optString("name").orEmpty()},(0 until g.length()).map{g.getJSONObject(it).optString("name")}.joinToString(", "),listOf("subgroup","note").filter{!x.isNull(it)}.map{x.optString(it).trim()}.filter{it.isNotBlank()}.joinToString(" · "))
    }.sortedWith(compareBy({it.date},{it.start}))
}
private fun cached(c:Context,w:LocalDate)=runCatching{parse(prefs(c).getString("week_$w","[]")!!)}.getOrDefault(emptyList())
private fun fetch(c:Context,w:LocalDate):List<Lesson>{
    val from=fmt.format(w.atStartOfDay(zone).toInstant())
    val to=fmt.format(w.plusWeeks(1).atStartOfDay(zone).toInstant().minusMillis(1))
    val u=URL("https://its.tltsu.ru/api/schedule/teacher?teacherId=15943&fromDate=$from&toDate=$to")
    val conn=u.openConnection() as HttpURLConnection
    conn.connectTimeout=12000;conn.readTimeout=15000
    conn.setRequestProperty("Accept","application/json")
    try {
        check(conn.responseCode==200){"HTTP "+conn.responseCode}
        val raw=conn.inputStream.bufferedReader().use{it.readText()}
        val lessons=parse(raw)
        prefs(c).edit().putString("week_$w",raw).putLong("updated_$w",System.currentTimeMillis()).apply()
        return lessons
    } finally {conn.disconnect()}
}
private fun alarms(c:Context){
    val p=prefs(c)
    val manager=c.getSystemService(AlarmManager::class.java)
    val now=System.currentTimeMillis()
    val desired=(cached(c,week(today()))+cached(c,week(today()).plusWeeks(1))).filter{it.begins()>now}.associateBy{it.id}
    fun intent(id:String)=Intent(c,ReminderReceiver::class.java).setData(Uri.parse("schedule://reminder/"+id))
    val flags=PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    val previous=p.getStringSet("alarm_ids",emptySet())!!.toSet()
    for(id in previous-desired.keys){PendingIntent.getBroadcast(c,0,intent(id),PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let{manager.cancel(it);it.cancel()}}
    for((id,l) in desired){
        val trigger=l.begins()-15*60000L
        if(trigger<=now)continue
        val i=intent(id).putExtra("name",l.name).putExtra("detail",l.time()+" · "+l.room+" · "+l.groups).putExtra("start",l.begins())
        val pi=PendingIntent.getBroadcast(c,0,i,flags)
        try {
            if(Build.VERSION.SDK_INT<31 || manager.canScheduleExactAlarms())manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi)
        } catch(e:SecurityException){manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi)}
    }
    p.edit().putStringSet("alarm_ids",desired.keys.toSet()).apply()
}
private fun channel(c:Context){
    c.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("lessons","Напоминания о парах",NotificationManager.IMPORTANCE_HIGH))
}
class ReminderReceiver:BroadcastReceiver(){
    override fun onReceive(c:Context,i:Intent){
        if(System.currentTimeMillis()>=i.getLongExtra("start",0))return
        if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        channel(c)
        val open=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n=Notification.Builder(c,"lessons").setSmallIcon(R.drawable.ic_notification).setContentTitle("Скоро пара: "+i.getStringExtra("name")).setContentText(i.getStringExtra("detail")).setStyle(Notification.BigTextStyle().bigText(i.getStringExtra("detail"))).setContentIntent(open).setAutoCancel(true).build()
        c.getSystemService(NotificationManager::class.java).notify(i.data.toString().hashCode(),n)
    }
}
private fun scheduleJob(c:Context){
    val js=c.getSystemService(JobScheduler::class.java)
    if(js.getPendingJob(15943)==null)js.schedule(JobInfo.Builder(15943,ComponentName(c,SyncJob::class.java)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(3600000L).setPersisted(true).build())
}
class BootReceiver:BroadcastReceiver(){
    override fun onReceive(c:Context,i:Intent){scheduleJob(c);alarms(c)}
}
class SyncJob:JobService(){
    override fun onStartJob(p:JobParameters):Boolean{
        executor.execute {
            var ok=true
            for(w in listOf(week(today()),week(today()).plusWeeks(1))){if(runCatching{fetch(this,w)}.isFailure)ok=false}
            alarms(this)
            jobFinished(p,!ok)
        }
        return true
    }
    override fun onStopJob(p:JobParameters)=true
}

class MainActivity:Activity(){
    private val bg=Color.parseColor("#111827")
    private val surface=Color.parseColor("#1E293B")
    private val muted=Color.parseColor("#B4C1D4")
    private val accent=Color.parseColor("#8EB5FF")
    private var selected=today()
    private lateinit var root:LinearLayout
    private lateinit var content:LinearLayout
    private lateinit var status:TextView
    private var loading=false
    private var allWeek=false
    private val handler=Handler(Looper.getMainLooper())
    private val ticker=object:Runnable{override fun run(){if(!loading)render();handler.postDelayed(this,60000)}}
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private fun rounded(c:Int,r:Int=16)=GradientDrawable().apply{setColor(c);cornerRadius=dp(r).toFloat()}
    private fun label(s:String,size:Float=15f,color:Int=Color.WHITE,bold:Boolean=false)=TextView(this).apply{text=s;textSize=size;setTextColor(color);if(bold)setTypeface(null,Typeface.BOLD)}
    private fun col()=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
    private fun row()=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    private fun button(s:String,action:()->Unit)=TextView(this).apply{text=s;textSize=14f;setTextColor(accent);gravity=Gravity.CENTER;setPadding(dp(10),dp(12),dp(10),dp(12));setOnClickListener{action()}}
    private fun LinearLayout.addGap(n:Int){addView(Space(this@MainActivity),LinearLayout.LayoutParams(1,dp(n)))}
    override fun onCreate(b:Bundle?){
        super.onCreate(b)
        channel(this);scheduleJob(this)
        root=col().apply{setBackgroundColor(bg);setPadding(dp(16),dp(12),dp(16),dp(8))}
        setContentView(root)
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),1)
        render();refresh(true)
    }
    override fun onResume(){super.onResume();alarms(this);handler.removeCallbacks(ticker);handler.postDelayed(ticker,60000)}
    override fun onPause(){handler.removeCallbacks(ticker);super.onPause()}
    private fun refresh(both:Boolean=false){
        if(loading)return
        loading=true;render()
        val w=week(selected)
        executor.execute{
            val result=runCatching{fetch(this,w)}
            if(both){for(other in listOf(week(today()),week(today()).plusWeeks(1)).filter{it!=w})runCatching{fetch(this,other)}}
            alarms(this)
            runOnUiThread{if(!isDestroyed){loading=false;render();if(result.isFailure)status.text="Не удалось обновить · сохранённые данные"}}
        }
    }
    private fun move(d:LocalDate){selected=d;render();if(!prefs(this).contains("week_"+week(d)))refresh()}
    private fun settings(){
        val exact=Build.VERSION.SDK_INT<31||getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        AlertDialog.Builder(this).setTitle("Уведомления").setMessage("Напоминания за 15 минут до каждой пары. Проверка расписания примерно раз в час. Точные будильники: "+if(exact)"разрешены" else "не разрешены. Разреши их для своевременных уведомлений.")
            .setPositiveButton("Точные будильники"){_,_->if(Build.VERSION.SDK_INT>=31)runCatching{startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+packageName)))}}
            .setNeutralButton("Настройки приложения"){_,_->startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+packageName)))}
            .setNegativeButton("Закрыть",null).show()
    }
    private fun render(){
        val w=week(selected);val lessons=cached(this,w)
        root.removeAllViews()
        val top=row();top.addView(label("Расписание ТГУ",25f,Color.WHITE,true),LinearLayout.LayoutParams(0,-2,1f));top.addView(button("Настройки"){settings()});root.addView(top)
        status=label(if(loading)"Обновление…" else if(prefs(this).contains("week_$w"))"Время Тольятти · сохранено" else "Нет загруженных данных",12f,muted)
        root.addView(status);root.addGap(8)
        val nav=row();nav.addView(button("‹"){move(selected.minusWeeks(1))});nav.addView(label(w.format(DateTimeFormatter.ofPattern("d MMM",ru))+" — "+w.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM",ru)),17f,Color.WHITE,true).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(0,-2,1f));nav.addView(button("›"){move(selected.plusWeeks(1))});root.addView(nav)
        val days=row()
        for(j in 0..6){
            val d=w.plusDays(j.toLong());val item=col().apply{gravity=Gravity.CENTER;setPadding(0,dp(8),0,dp(8));background=rounded(if(d==selected)Color.parseColor("#315DA8") else surface,12);setOnClickListener{selected=d;allWeek=false;render()}}
            item.addView(label(listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс")[j],12f,muted));item.addView(label(d.dayOfMonth.toString(),17f,Color.WHITE,true));item.addView(label(lessons.count{it.date==d}.let{if(it==0)"·" else "$it"},11f,accent))
            days.addView(item,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(dp(2),0,dp(2),0)})
        }
        root.addView(days)
        val controls=row();controls.addView(button("Сегодня"){allWeek=false;move(today())},LinearLayout.LayoutParams(0,-2,1f));controls.addView(button(if(allWeek)"По дням" else "Вся неделя"){allWeek=!allWeek;render()},LinearLayout.LayoutParams(0,-2,1f));controls.addView(button("Обновить"){refresh(true)},LinearLayout.LayoutParams(0,-2,1f));root.addView(controls)
        val scroll=ScrollView(this);content=col();scroll.addView(content);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val now=System.currentTimeMillis()
        val next=(cached(this,week(today()))+cached(this,week(today()).plusWeeks(1))).firstOrNull{it.ends()>now}
        if(next!=null){
            val banner=col().apply{background=rounded(Color.parseColor("#233A5C"));setPadding(dp(14),dp(12),dp(14),dp(12))}
            banner.addView(label(if(next.begins()<=now)"Сейчас идёт пара" else "Следующая пара · "+next.date.format(dayFmt),13f,accent,true))
            banner.addGap(4);banner.addView(label(next.name,17f,Color.WHITE,true));banner.addGap(4);banner.addView(label(next.time()+" · "+next.room,14f,muted));content.addView(banner);content.addGap(16)
        }
        val dates=if(allWeek)(0..6).map{w.plusDays(it.toLong())} else listOf(selected)
        for(d in dates){
            val day=lessons.filter{it.date==d}
            content.addView(label(d.format(dayFmt).replaceFirstChar{it.uppercase(ru)}+" · "+day.size+" пар",17f,Color.WHITE,true));content.addGap(8)
            if(day.isEmpty()){content.addView(label(if(prefs(this).contains("week_$w"))"Занятий нет" else "Расписание не загружено",15f,muted));content.addGap(16)}
            for(l in day){
                val line=row().apply{gravity=Gravity.TOP}
                val times=col();times.addView(label(l.start.toString(),17f,Color.WHITE,true));times.addView(label(l.end.toString(),13f,muted));times.addGap(5);times.addView(label(l.number.toString()+" пара",11f,muted))
                line.addView(times,LinearLayout.LayoutParams(dp(68),-2))
                val card=col().apply{background=rounded(surface);setPadding(dp(13),dp(11),dp(13),dp(11))}
                val typeColor=when(l.type){"Лекция"->accent;"Лабораторная"->Color.parseColor("#72D9BA");else->Color.parseColor("#F2C581")}
                card.addView(label(l.type+"  ·  "+l.room,13f,typeColor,true));card.addGap(5);card.addView(label(l.name,17f,Color.WHITE,true));card.addGap(6);card.addView(label(l.groups,14f,muted));if(l.note.isNotBlank())card.addView(label(l.note,12f,muted))
                line.addView(card,LinearLayout.LayoutParams(0,-2,1f));content.addView(line);content.addGap(10)
            }
            content.addGap(10)
        }
    }
}
