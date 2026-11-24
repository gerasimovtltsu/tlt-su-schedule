package ru.tltsu.schedule
import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.Button
class MainActivity : Activity() {
 override fun onCreate(savedInstanceState: Bundle?) {
 super.onCreate(savedInstanceState)
 val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
 val web = WebView(this).apply { webViewClient = WebViewClient(); settings.javaScriptEnabled = true; settings.domStorageEnabled = true }
 val refresh = Button(this).apply { text = "Обновить расписание"; setOnClickListener { web.reload() } }
 root.addView(refresh); root.addView(web, LinearLayout.LayoutParams(-1,0,1f)); setContentView(root)
 web.loadUrl("https://rasp.tltsu.ru/teacher/15943?isWeek=true")
 }
}
