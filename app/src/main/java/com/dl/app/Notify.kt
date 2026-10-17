package com.dl.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Native replacement for the HTML's reminder engine. Same rules (class lead time, objective due-morning /
 * evening-before / overdue), but scheduled with AlarmManager so they arrive even when the app is closed.
 */
object Notifier {
    const val CH = "dl-reminders"
    @Volatile var visible = false

    fun permGranted(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun channel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true); vibrationPattern = longArrayOf(0, 200, 100, 200)
        })
    }

    fun notify(ctx: Context, id: Int, title: String, body: String) {
        channel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = android.app.Notification.Builder(ctx, CH).setSmallIcon(R.drawable.ic_launcher).setContentTitle(title)
            .setContentText(body).setContentIntent(open).setAutoCancel(true).build()
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(id, n) }
    }

    /** The HTML shows an in-app alert while visible and a system notification otherwise. */
    fun deliver(ctx: Context, id: Int, title: String, body: String) {
        if (visible) {
            Handler(Looper.getMainLooper()).post { toast(title + if (body.isNotEmpty()) " · $body" else ""); Sfx.vibrate(longArrayOf(0, 180, 90, 180)) }
        } else notify(ctx, id, title, body)
    }

    private fun pi(ctx: Context, code: Int, title: String, body: String, flags: Int): PendingIntent? {
        val i = Intent(ctx, ReminderReceiver::class.java).putExtra("code", code).putExtra("title", title).putExtra("body", body)
        return PendingIntent.getBroadcast(ctx, code, i, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    fun scheduleAt(ctx: Context, key: String, at: Long, title: String, body: String): Int {
        val code = key.hashCode()
        val am = ctx.getSystemService(AlarmManager::class.java)
        val p = pi(ctx, code, title, body, PendingIntent.FLAG_UPDATE_CURRENT) ?: return code
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        return code
    }

    fun cancel(ctx: Context, key: String) = cancelCode(ctx, key.hashCode())
    private fun cancelCode(ctx: Context, code: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        pi(ctx, code, "", "", PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
    }

    /** Timers / focus sessions: only when reminders and "Timers & focus" are on. */
    fun scheduleTimer(ctx: Context, key: String, endAt: Long, title: String, body: String) {
        val n = Store.nprefs()
        if (n.on && n.timers) scheduleAt(ctx, "timer-$key", endAt, title, body)
    }
    fun cancelTimer(ctx: Context, key: String) = cancel(ctx, "timer-$key")

    private fun hm(s: String): Pair<Int, Int> { val p = s.split(":"); return (p.getOrNull(0)?.toIntOrNull() ?: 8) to (p.getOrNull(1)?.toIntOrNull() ?: 0) }
    private fun ms(dt: LocalDateTime) = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Synchronized
    fun rescheduleAll(ctx: Context) {
        val prefs = ctx.getSharedPreferences("dl_alarms", 0)
        prefs.getStringSet("codes", emptySet())?.forEach { cancelCode(ctx, it.toInt()) }
        val codes = mutableSetOf<String>()
        val d = Store.d; val n = Store.nprefs()
        if (!n.on) { prefs.edit().putStringSet("codes", codes).apply(); return }
        val now = System.currentTimeMillis(); val today = LocalDate.now()
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        fun add(key: String, at: Long, title: String, body: String) {
            if (at > now + 1000 && at >= n.since) codes += scheduleAt(ctx, key, at, title, body).toString()
        }

        if (n.cls) d.classes.forEach { c ->
            if (c.time.isEmpty() || c.days.isEmpty()) return@forEach
            val (h, m) = hm(c.time)
            for (k in 0..7) {
                val date = today.plusDays(k.toLong())
                if (dayNames[date.dayOfWeek.value - 1] !in c.days) continue
                val start = date.atTime(h, m)
                val parts = listOf(if (n.clsLead > 0) "Starts in ${n.clsLead} min" else "Starting now", fmtTime(c.time), c.location).filter { it.isNotEmpty() }
                add("cls:${c.id}:$date", ms(start.minusMinutes(n.clsLead.toLong())), c.name, parts.joinToString(" · "))
            }
        }
        if (n.hw) d.homework.filter { !it.done && it.due.isNotEmpty() }.forEach { h ->
            val due = runCatching { LocalDate.parse(h.due) }.getOrNull() ?: return@forEach
            val (oh, om) = hm(n.hwTime); val (eh, em) = hm(n.hwEveTime)
            fun go(kind: String, at: LocalDateTime, title: String) {
                val t = ms(at)
                if (t >= h.created) add("hw:${h.id}:$kind:${h.due}", t, title, h.title)
            }
            go("today", due.atTime(oh, om), "Due today")
            if (n.hwEve) go("eve", due.minusDays(1).atTime(eh, em), "Due tomorrow")
            go("late", due.plusDays(1).atTime(oh, om), "Overdue")
        }
        prefs.edit().putStringSet("codes", codes).apply()
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        Store.init(ctx)
        Notifier.deliver(ctx, i.getIntExtra("code", 1), i.getStringExtra("title") ?: "Reminder", i.getStringExtra("body") ?: "")
        Notifier.rescheduleAll(ctx)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) { Store.init(ctx); Notifier.rescheduleAll(ctx) }
}
