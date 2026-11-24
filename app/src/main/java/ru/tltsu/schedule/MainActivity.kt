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
    val lead=p.getInt("lead",15)
    val desired=(cached(c,week(today()))+cached(c,week(today()).plusWeeks(1))).filter{it.begins()>now}.associateBy{it.id}
    fun intent(id:String)=Intent(c,ReminderReceiver::class.java).setData(Uri.parse("schedule://reminder/"+id))
    val flags=PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    val previous=p.getStringSet("alarm_ids",emptySet())!!.toSet()
    for(id in previous-desired.keys){PendingIntent.getBroadcast(c,0,intent(id),PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let{manager.cancel(it);it.cancel()}}
    for((id,l) in desired){
        val trigger=l.begins()-lead*60000L
        if(p.getLong("sent_$id",0)==l.begins()){PendingIntent.getBroadcast(c,0,intent(id),PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let{manager.cancel(it);it.cancel()};continue}
        val fireAt=maxOf(trigger,now+1000L)
        val i=intent(id).putExtra("name",l.name).putExtra("detail",l.time()+" · "+l.room+" · "+l.groups).putExtra("start",l.begins())
        val pi=PendingIntent.getBroadcast(c,0,i,flags)
        try {
            if(Build.VERSION.SDK_INT<31 || manager.canScheduleExactAlarms())manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,fireAt,pi)
            else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,fireAt,pi)
        } catch(e:SecurityException){manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,fireAt,pi)}
    }
    p.edit().putStringSet("alarm_ids",desired.keys.toSet()).apply()
}
private fun channel(c:Context){
    val ch=NotificationChannel("lessons_v2","Напоминания о парах",NotificationManager.IMPORTANCE_HIGH).apply {
        enableVibration(true)
        vibrationPattern=longArrayOf(0,250,150,250)
    }
    c.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
}
class ReminderReceiver:BroadcastReceiver(){
    override fun onReceive(c:Context,i:Intent){
        val now=System.currentTimeMillis()
        val start=i.getLongExtra("start",0)
        if(now>=start)return
        val id=i.data?.lastPathSegment
        val test=i.data?.host=="test"
        if(!test && id!=null){
            val lesson=(cached(c,week(today()))+cached(c,week(today()).plusWeeks(1))).firstOrNull{it.id==id && it.begins()==start} ?: return
            if(now<lesson.begins()-prefs(c).getInt("lead",15)*60000L){alarms(c);return}
            if(prefs(c).getLong("sent_$id",0)==start)return
        }
        if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        channel(c)
        val open=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n=Notification.Builder(c,"lessons_v2").setSmallIcon(R.drawable.ic_notification).setContentTitle("Скоро пара: "+i.getStringExtra("name")).setContentText(i.getStringExtra("detail")).setStyle(Notification.BigTextStyle().bigText(i.getStringExtra("detail"))).setContentIntent(open).setAutoCancel(true).build()
        c.getSystemService(NotificationManager::class.java).notify(i.data.toString().hashCode(),n)
        if(!test && id!=null)prefs(c).edit().putLong("sent_$id",start).apply()
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
    private var downX=0f
    private var downY=0f
    private var downTime=0L
    private var syncError=false
    override fun dispatchTouchEvent(e:MotionEvent):Boolean {
        if(e.actionMasked==MotionEvent.ACTION_DOWN){downX=e.x;downY=e.y;downTime=e.eventTime}
        if(e.actionMasked==MotionEvent.ACTION_UP && !allWeek && kotlin.math.abs(e.x-downX)>dp(90) && kotlin.math.abs(e.y-downY)<dp(55) && e.eventTime-downTime<700){val cancel=MotionEvent.obtain(e);cancel.action=MotionEvent.ACTION_CANCEL;super.dispatchTouchEvent(cancel);cancel.recycle();move(selected.plusDays(if(e.x<downX)1L else -1L));return true}
        return super.dispatchTouchEvent(e)
    }
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
        root=col().apply{setBackgroundColor(bg);setPadding(dp(14),dp(5),dp(14),dp(4))}
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
            runOnUiThread{if(!isDestroyed){loading=false;syncError=result.isFailure;render();if(week(selected)!=w && !prefs(this).contains("week_"+week(selected)))refresh()}}
        }
    }
    private fun move(d:LocalDate){selected=d;render();if(!prefs(this).contains("week_"+week(d)))refresh()}
    private fun settings(){
        val exact=Build.VERSION.SDK_INT<31||getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        val lead=prefs(this).getInt("lead",15)
        val enabled=getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        val options=arrayOf("За $lead мин. · изменить","Тестовое уведомление","Точные будильники · "+if(exact)"разрешены" else "не разрешены","Уведомления · "+if(enabled)"включены" else "выключены","Настройки приложения","Тест будильника через 30 секунд")
        AlertDialog.Builder(this).setTitle("Напоминания").setItems(options){_,idx->
            when(idx){
                0->{val values=intArrayOf(5,10,15,20,30);AlertDialog.Builder(this).setTitle("Напоминать до пары").setSingleChoiceItems(values.map{"$it мин."}.toTypedArray(),values.indexOf(lead)){dialog,which->prefs(this).edit().putInt("lead",values[which]).apply();alarms(this);dialog.dismiss()}.setNegativeButton("Отмена",null).show()}
                1->{if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),1)}else{val i=Intent(this,ReminderReceiver::class.java).setData(Uri.parse("schedule://test")).putExtra("name","Тестовое уведомление").putExtra("detail","Напоминания о парах включены").putExtra("start",System.currentTimeMillis()+60000);sendBroadcast(i)}}
                2->{if(Build.VERSION.SDK_INT>=31)runCatching{startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+packageName)))}}
                3->startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,packageName))
                4->startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+packageName)))
                5->{
                    val am=getSystemService(AlarmManager::class.java)
                    if(Build.VERSION.SDK_INT>=31 && !am.canScheduleExactAlarms()){Toast.makeText(this,"Разрешите точные будильники",Toast.LENGTH_LONG).show();return@setItems}
                    val at=System.currentTimeMillis()+30000L
                    val test=Intent(this,ReminderReceiver::class.java).setData(Uri.parse("schedule://test/alarm")).putExtra("name","Проверка будильника").putExtra("detail","Фоновое напоминание через AlarmManager").putExtra("start",at+600000L)
                    val pi=PendingIntent.getBroadcast(this,0,test,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    try{am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi);Toast.makeText(this,"Заблокируйте экран. Проверка через 30 секунд",Toast.LENGTH_LONG).show()}catch(e:SecurityException){Toast.makeText(this,"Нет разрешения на точные будильники",Toast.LENGTH_LONG).show()}
                }
            }
        }.setNegativeButton("Закрыть",null).show()
    }
    private fun iconButton(res:Int,description:String,action:()->Unit)=ImageButton(this).apply{setImageResource(res);setColorFilter(accent);setBackgroundColor(Color.TRANSPARENT);contentDescription=description;setPadding(dp(11),dp(11),dp(11),dp(11));scaleType=ImageView.ScaleType.FIT_CENTER;layoutParams=LinearLayout.LayoutParams(dp(46),dp(46));setOnClickListener{action()}}
    private fun render(){
        val w=week(selected);val lessons=cached(this,w)
        root.removeAllViews()
        val top=row();top.addView(label("Расписание ТГУ",21f,Color.WHITE,true),LinearLayout.LayoutParams(0,-2,1f));top.addView(iconButton(android.R.drawable.ic_popup_sync,"Обновить"){refresh(true)});top.addView(iconButton(android.R.drawable.ic_menu_preferences,"Настройки"){settings()});root.addView(top)
        val updated=prefs(this).getLong("updated_$w",0)
        status=label(if(loading)"Обновление…" else if(syncError)"Нет связи · сохранённые данные" else if(updated>0)"Обновлено "+Instant.ofEpochMilli(updated).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")) else "Нет загруженных данных",11f,muted)
        root.addView(status)
        val nav=row();nav.addView(button("‹"){move(selected.minusWeeks(1))});nav.addView(label(w.format(DateTimeFormatter.ofPattern("d MMM",ru))+" — "+w.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM",ru)),17f,Color.WHITE,true).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(0,-2,1f));nav.addView(button("›"){move(selected.plusWeeks(1))});root.addView(nav)
        val days=row()
        for(j in 0..6){
            val d=w.plusDays(j.toLong());val item=col().apply{gravity=Gravity.CENTER;setPadding(0,dp(5),0,dp(5));background=rounded(if(!allWeek && d==selected)Color.parseColor("#315DA8") else Color.TRANSPARENT,10);setOnClickListener{selected=d;allWeek=false;render()}}
            item.addView(label(listOf("Пн","Вт","Ср","Чт","Пт","Сб","Вс")[j],11f,muted).apply{gravity=Gravity.CENTER});item.addView(label(d.dayOfMonth.toString(),16f,Color.WHITE,true).apply{gravity=Gravity.CENTER});item.addView(label(if(lessons.any{it.date==d})"●" else " ",8f,accent).apply{gravity=Gravity.CENTER})
            days.addView(item,LinearLayout.LayoutParams(0,-2,1f).apply{setMargins(dp(2),0,dp(2),0)})
        }
        root.addView(days)
        root.addGap(8)
        val scroll=ScrollView(this);content=col();scroll.addView(content);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        val now=System.currentTimeMillis()
        val next=(cached(this,week(today()))+cached(this,week(today()).plusWeeks(1))).firstOrNull{it.ends()>now}
        if(next!=null){
            val banner=col().apply{background=rounded(Color.parseColor("#233A5C"));setPadding(dp(11),dp(8),dp(11),dp(8))}
            banner.addView(label(if(next.begins()<=now)"Сейчас идёт пара" else if(next.date==today())"Через "+((next.begins()-now+59999)/60000)+" мин." else if(next.date==today().plusDays(1))"Завтра в "+next.start else next.date.format(dayFmt)+" · "+next.start,13f,accent,true))
            banner.addGap(2);banner.addView(label(next.name,15f,Color.WHITE,true));banner.addGap(2);banner.addView(label(next.type+" · "+next.room+" · "+next.time(),12f,muted));content.addView(banner);content.addGap(10)
        }
        val dates=if(allWeek)(0..6).map{w.plusDays(it.toLong())} else listOf(selected)
        for(d in dates){
            val day=lessons.filter{it.date==d}
            content.addView(label(d.format(dayFmt).replaceFirstChar{it.uppercase(ru)}+" · "+day.size+" пар",17f,Color.WHITE,true));content.addGap(8)
            if(day.isEmpty()){content.addView(label(if(prefs(this).contains("week_$w"))"Занятий нет" else "Расписание не загружено",15f,muted));content.addGap(16)}
            for(l in day){
                val line=row().apply{gravity=Gravity.TOP}
                val times=col();times.addView(label(l.start.toString(),15f,Color.WHITE,true));times.addView(label(l.end.toString(),12f,muted))
                line.addView(times,LinearLayout.LayoutParams(dp(54),-2))
                val card=col().apply{background=rounded(surface,10);setPadding(dp(10),dp(7),dp(10),dp(7))}
                val typeColor=when(l.type){"Лекция"->accent;"Лабораторная"->Color.parseColor("#72D9BA");else->Color.parseColor("#F2C581")}
                card.addView(label(l.name,15f,Color.WHITE,true));card.addGap(2);card.addView(label(l.type+" · "+l.room,12f,typeColor,true));card.addGap(2);card.addView(label(l.groups,12f,muted));if(l.note.isNotBlank())card.addView(label(l.note,11f,muted));if(l.ends()<now)line.alpha=0.68f
                line.addView(card,LinearLayout.LayoutParams(0,-2,1f));content.addView(line);content.addGap(6)
            }
            content.addGap(8)
        }
        val bottom=row()
        bottom.addView(button("Сегодня"){allWeek=false;move(today())},LinearLayout.LayoutParams(0,-2,1f))
        bottom.addView(button(if(allWeek)"● Неделя" else "Неделя"){allWeek=!allWeek;render()},LinearLayout.LayoutParams(0,-2,1f))
        bottom.addView(button("Напоминания"){settings()},LinearLayout.LayoutParams(0,-2,1f))
        root.addView(bottom)
    }
}
