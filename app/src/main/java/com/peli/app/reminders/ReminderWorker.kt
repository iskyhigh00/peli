package com.peli.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.peli.app.MainActivity
import com.peli.app.PeliApp
import com.peli.app.R
import com.peli.app.data.Reminders
import com.peli.app.log.AppLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Revisa cada medio día las películas de "Quiero verla" con estreno cercano y avisa con una notificación. */
class ReminderWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = context.applicationContext as PeliApp
        val store = app.reminderStore
        if (!store.enabled || !canNotify(context)) return Result.success()
        val repo = app.repository
        val upcoming = repo.refreshUpcomingWatchlist()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val due = Reminders.due(upcoming, today, store.notified)
        AppLog.i("Avisos", "revisadas ${upcoming.size} por estrenar; avisos nuevos: ${due.size}")
        if (due.isEmpty()) return Result.success()
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        var recorded = store.notified
        due.forEach { r ->
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_peli)
                .setContentTitle(r.title)
                .setContentText(r.text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(r.entry.movieId, n) }
                .onSuccess { recorded = recorded + Reminders.toRecord(r) }
        }
        store.notified = recorded
        return Result.success()
    }

    companion object {
        private const val CHANNEL = "estrenos"
        private const val WORK = "avisos-estrenos"

        fun canNotify(context: Context): Boolean =
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
                NotificationManagerCompat.from(context).areNotificationsEnabled()

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Estrenos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Aviso cuando se acerca el estreno de una película que quieres ver"
            })
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
