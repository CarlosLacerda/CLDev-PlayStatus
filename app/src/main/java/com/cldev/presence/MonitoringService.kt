
package com.cldev.presence

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MonitoringService : Service() {

    companion object {
        const val ACTION_START = "com.cldev.presence.START"
        const val ACTION_STOP = "com.cldev.presence.STOP"
        const val ACTION_UPDATE = "com.cldev.presence.UPDATE"

        const val EXTRA_HOST = "host"
        const val EXTRA_MONITORING = "monitoring"
        const val EXTRA_STATUS = "status"
        const val EXTRA_GAME_TITLE = "game_title"
        const val EXTRA_TITLE_ID = "title_id"
        const val EXTRA_HTTP_CODE = "http_code"
        const val EXTRA_LAST_UPDATE = "last_update"
        const val EXTRA_METADATA_STATUS = "metadata_status"
        const val EXTRA_METADATA_ID = "metadata_id"
        const val EXTRA_METADATA_TITLE = "metadata_title"
        const val EXTRA_RELEASE_DATE = "release_date"
        const val EXTRA_COVER_URL = "cover_url"

        private const val CHANNEL_ID = "cldev_monitoring"
        private const val NOTIFICATION_ID = 1001
        private const val POLLING_INTERVAL = 10_000L
    }


    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var monitorJob: Job? = null
    private var host = "192.168.15.5"
    private var wakeLock: PowerManager.WakeLock? = null

    private val cache by lazy {
        GameCache(applicationContext)
    }

    private external fun nativeInitializeDiscord(): Boolean
    private external fun nativeRunDiscordCallbacks()
    private external fun nativeShutdownDiscord()

    private external fun nativeUpdateRichPresence(
        gameTitle: String,
        titleId: String,
        coverUrl: String
    )

    private external fun nativeClearRichPresence()

    private var discordInitialized = false
    private var callbackJob: Job? = null
    private var lastPresenceTitleId: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_STOP -> {
                stopMonitoring()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                host = intent.getStringExtra(EXTRA_HOST)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: "192.168.15.5"
            }

            else -> {
                return START_NOT_STICKY
            }
        }

        startForeground(
            NOTIFICATION_ID,
            buildNotification("Iniciando monitoramento...")
        )

        acquireWakeLock()

        if (!discordInitialized) {
            discordInitialized = nativeInitializeDiscord()

            if (discordInitialized) {
                callbackJob = serviceScope.launch {
                    while (isActive) {
                        nativeRunDiscordCallbacks()
                        delay(100L)
                    }
                }
            }
        }

        if (monitorJob?.isActive != true) {
            startMonitoring()
        }

        return START_STICKY
    }

    private fun startMonitoring() {
        monitorJob = serviceScope.launch {

            var lastMetadataTitleId: String? = null

            while (isActive) {

                try {
                    val result = WebManClient().readSystemInfo(host)

                    val lastUpdate = SimpleDateFormat(
                        "HH:mm:ss",
                        Locale.getDefault()
                    ).format(Date())

                    if (result.httpCode == 200) {

                        val gameTitle = result.gameTitle
                        val titleId = result.titleId

                        if (gameTitle != null && titleId != null) {

                            var metadata: GameMetadata? = null
                            var metadataStatus =
                                "Consultando catálogo local..."

                            if (lastMetadataTitleId != titleId) {

                                lastMetadataTitleId = titleId

                                metadata = cache.get(titleId)

                                if (metadata != null) {

                                    metadataStatus =
                                        "Informações carregadas do catálogo local."

                                } else {

                                    metadataStatus =
                                        "Pesquisando na TheGamesDB..."

                                    try {
                                        metadata = TheGamesDbClient()
                                            .searchGame(gameTitle)

                                        if (metadata != null) {
                                            cache.save(titleId, metadata)

                                            metadataStatus =
                                                "Informações encontradas e salvas no catálogo!"
                                        } else {
                                            metadataStatus =
                                                "Jogo não encontrado na TheGamesDB."
                                        }

                                    } catch (e: Exception) {
                                        metadataStatus =
                                            "Erro na consulta: ${e.message}"
                                    }
                                }

                            } else {
                                metadata = cache.get(titleId)
                                metadataStatus =
                                    "Jogo identificado pelo WebMAN."
                            }

                            if (lastPresenceTitleId != titleId && discordInitialized) {
                                nativeUpdateRichPresence(
                                    gameTitle,
                                    titleId,
                                    metadata?.coverUrl.orEmpty()
                                )
                                lastPresenceTitleId = titleId
                            }

                            updateNotification(gameTitle)

                            sendUpdate(
                                status = "PS3 conectado • Jogo identificado",
                                gameTitle = gameTitle,
                                titleId = titleId,
                                httpCode = result.httpCode,
                                lastUpdate = lastUpdate,
                                metadata = metadata,
                                metadataStatus = metadataStatus
                            )

                        } else {

                            lastMetadataTitleId = null
                            clearDiscordPresence()

                            sendUpdate(
                                status = "PS3 conectado • Nenhum jogo identificado",
                                gameTitle = null,
                                titleId = null,
                                httpCode = result.httpCode,
                                lastUpdate = lastUpdate,
                                metadata = null,
                                metadataStatus =
                                    "Aguardando identificação do jogo"
                            )

                            updateNotification("PS3 conectado • Sem jogo")
                        }

                    } else {

                        clearDiscordPresence()

                        sendUpdate(
                            status = "WebMAN respondeu com HTTP ${result.httpCode}",
                            gameTitle = null,
                            titleId = null,
                            httpCode = result.httpCode,
                            lastUpdate = lastUpdate,
                            metadata = null,
                            metadataStatus =
                                "Aguardando resposta válida do PS3"
                        )
                    }

                } catch (e: CancellationException) {
                    throw e

                } catch (e: Exception) {

                    clearDiscordPresence()

                    sendUpdate(
                        status = "Falha na comunicação: ${e.message}",
                        gameTitle = null,
                        titleId = null,
                        httpCode = null,
                        lastUpdate = "Falha na consulta",
                        metadata = null,
                        metadataStatus = "Consulta indisponível"
                    )
                }

                delay(POLLING_INTERVAL)
            }
        }
    }

    private fun clearDiscordPresence() {
        if (discordInitialized && lastPresenceTitleId != null) {
            nativeClearRichPresence()
            lastPresenceTitleId = null
        }
    }

    private fun sendUpdate(
        status: String,
        gameTitle: String?,
        titleId: String?,
        httpCode: Int?,
        lastUpdate: String,
        metadata: GameMetadata?,
        metadataStatus: String
    ) {
        val intent = Intent(ACTION_UPDATE).apply {
            setPackage(packageName)

            putExtra(EXTRA_MONITORING, true)
            putExtra(EXTRA_STATUS, status)
            putExtra(EXTRA_GAME_TITLE, gameTitle)
            putExtra(EXTRA_TITLE_ID, titleId)
            putExtra(EXTRA_HTTP_CODE, httpCode ?: -1)
            putExtra(EXTRA_LAST_UPDATE, lastUpdate)
            putExtra(EXTRA_METADATA_STATUS, metadataStatus)

            putExtra(EXTRA_METADATA_ID, metadata?.id ?: -1)
            putExtra(EXTRA_METADATA_TITLE, metadata?.title)
            putExtra(EXTRA_RELEASE_DATE, metadata?.releaseDate)
            putExtra(EXTRA_COVER_URL, metadata?.coverUrl)
        }

        sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitoramento do PS3",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description =
                    "Notificação do monitoramento do CLDev Discord Presence"
            }

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {

        val stopIntent = Intent(this, MonitoringService::class.java).apply {
            action = ACTION_STOP
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        val openIntent = packageManager
            .getLaunchIntentForPackage(packageName)

        val openPendingIntent = openIntent?.let {
            PendingIntent.getActivity(
                this,
                2,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("CLDev Discord Presence")
            .setContentText(message)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Parar",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification(message: String) {
        val manager =
            getSystemService(NotificationManager::class.java)

        manager.notify(
            NOTIFICATION_ID,
            buildNotification(message)
        )
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return

        val powerManager =
            getSystemService(POWER_SERVICE) as PowerManager

        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:MonitoringService"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null

        clearDiscordPresence()

        callbackJob?.cancel()
        callbackJob = null

        if (discordInitialized) {
            nativeShutdownDiscord()
            discordInitialized = false
        }

        serviceScope.cancel()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        wakeLock = null

        val intent = Intent(ACTION_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_MONITORING, false)
            putExtra(EXTRA_STATUS, "Monitoramento parado")
        }

        sendBroadcast(intent)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        serviceScope.cancel()

        clearDiscordPresence()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        wakeLock = null

        callbackJob?.cancel()
        callbackJob = null

        if (discordInitialized) {
            nativeShutdownDiscord()
            discordInitialized = false
        }

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
