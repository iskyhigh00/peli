package com.peli.app.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.peli.app.MainActivity
import com.peli.app.PeliApp
import com.peli.app.R
import com.peli.app.data.WeekendPlan
import com.peli.app.log.AppLog

/** Alarma de los avisos de fin de semana: siempre queda programada la siguiente (miércoles, jueves y viernes). */
object WeekendAlarm {
    private const val CHANNEL = "fin_de_semana"

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 4242, Intent(context, WeekendReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Programa el próximo aviso (reemplaza el anterior). Sin permiso de alarma exacta: puede atrasarse unos minutos. */
    fun schedule(context: Context) {
        val (at, slot) = WeekendPlan.next(System.currentTimeMillis())
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context)) }
            .onFailure { AppLog.w("Avisos", "no se pudo programar el aviso de fin de semana", it) }
        AppLog.d("Avisos", "próximo aviso de fin de semana: ${java.util.Date(at)} (${slot.title})")
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
    }

    internal fun notifyNow(context: Context, now: Long) {
        val slot = WeekendPlan.due(now) ?: return
        showFor(context, slot.id, slot.title, slot.text)
    }

    private fun showFor(context: Context, id: Int, title: String, text: String) {
        if (!ReminderWorker.canNotify(context)) return
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Fin de semana", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Recordatorio de preparar las películas del fin de semana"
            })
        }
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_peli)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(2_000_000 + id, n) }
    }
}

/** Se dispara a la hora del aviso: lo muestra y deja programado el siguiente. */
class WeekendReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as PeliApp
        if (app.reminderStore.weekendEnabled) WeekendAlarm.notifyNow(context, System.currentTimeMillis())
        WeekendAlarm.schedule(context)
    }
}

/** Al reiniciar el teléfono las alarmas se pierden: se vuelve a programar la siguiente. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as PeliApp
        if (app.reminderStore.weekendEnabled) WeekendAlarm.schedule(context)
    }
}
