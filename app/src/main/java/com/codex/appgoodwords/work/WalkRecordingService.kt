package com.codex.appgoodwords.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import com.codex.appgoodwords.AppGoodWordsApplication
import com.codex.appgoodwords.data.GeoPoint
import com.codex.appgoodwords.data.WalkPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 걷기를 기록하는 동안 GPS를 받는 서비스(사용자가 정함, 2026-11-07).
 *
 * **"걷기 기록 시작"을 누른 동안에만 위치를 받습니다.** 화면을 끄거나 다른 앱으로 가도 이어지게 앞쪽 서비스(위치 종류)로
 * 돌고, 그동안 "걷기 기록 중" 알림이 떠 있습니다. 끝내기는 화면이나 알림에서 합니다. 배경 위치 권한은 쓰지 않습니다 —
 * 사용자가 화면에서 시작한 기록이라 "앱을 쓰는 동안" 권한으로 충분합니다.
 *
 * 받은 점은 [WalkPath.accept]가 거르고, 몇 점마다 DB에 이어 적습니다. 서비스가 죽어도 그때까지의 길은 남고, 다음에 앱이
 * 켜질 때 마무리됩니다([com.codex.appgoodwords.data.WalkRepository.finishInterrupted]).
 */
class WalkRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startJob: Job? = null
    private var tickJob: Job? = null

    // 아래 넷은 메인 스레드(위치 콜백)에서만 고칩니다. 저장할 때는 사본을 넘깁니다.
    private var walkSyncId: String? = null
    private var startedAt = 0L
    private val points = mutableListOf<GeoPoint>()
    private var lastFix: WalkPath.Fix? = null
    private var distanceMeters = 0.0
    private var unsavedPoints = 0
    private var stopping = false

    private val listener = LocationListenerCompat { location -> onLocation(location) }

    private val repository
        get() = (application as AppGoodWordsApplication).container.walkRepository

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stop()
            return START_NOT_STICKY
        }
        // 앞쪽 서비스는 시작하라는 부름마다 몇 초 안에 알림을 띄워야 합니다(이미 기록 중이어도). 권한이 없으면 띄울 수도
        // 없으니 바로 접습니다.
        if (!hasLocationPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        if (_recording.value) return START_NOT_STICKY
        _recording.value = true
        startJob = scope.launch {
            val walk = runCatching { repository.startWalk() }.getOrNull()
            withContext(Dispatchers.Main) {
                if (walk == null) {
                    stop()
                    return@withContext
                }
                walkSyncId = walk.syncId
                startedAt = walk.startedAt
                // 서비스가 다시 떴으면 적어 둔 길에 이어 갑니다.
                points += WalkPath.decode(walk.path)
                distanceMeters = walk.distanceMeters.toDouble()
                requestUpdates()
                updateNotification()
            }
        }
        tickJob = scope.launch {
            // 서 있는 동안에도 알림의 시간은 흘러야 합니다.
            while (isActive) {
                delay(TICK_MILLIS)
                withContext(Dispatchers.Main) { updateNotification() }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        removeUpdates()
        // 끝내기 없이 죽으면(시스템이 거둬 감) 그때까지의 길을 남깁니다. 다음에 앱이 켜질 때 마무리됩니다.
        // 이 서비스의 코루틴은 곧 거두므로 앱 수명의 자리에서 적습니다.
        if (!stopping) saveNow((application as AppGoodWordsApplication).applicationScope)
        _recording.value = false
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        ServiceCompat.startForeground(
            this,
            AppNotifications.walkNotificationId,
            notification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        )
    }

    @SuppressLint("MissingPermission") // 시작할 때 권한을 확인했습니다(hasLocationPermission).
    private fun requestUpdates() {
        if (!hasLocationPermission(this)) return
        val manager = getSystemService(LocationManager::class.java) ?: return
        val provider = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && manager.hasProvider(LocationManager.FUSED_PROVIDER)) {
            LocationManager.FUSED_PROVIDER
        } else {
            LocationManager.GPS_PROVIDER
        }
        val request = LocationRequestCompat.Builder(INTERVAL_MILLIS)
            .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(INTERVAL_MILLIS / 2)
            .build()
        runCatching {
            LocationManagerCompat.requestLocationUpdates(manager, provider, request, ContextCompat.getMainExecutor(this), listener)
        }
    }

    private fun removeUpdates() {
        val manager = getSystemService(LocationManager::class.java) ?: return
        runCatching { LocationManagerCompat.removeUpdates(manager, listener) }
    }

    private fun onLocation(location: Location) {
        if (stopping) return
        val fix = WalkPath.Fix(
            lat = location.latitude,
            lng = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE,
            time = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
        )
        val step = WalkPath.accept(lastFix, fix) ?: return
        lastFix = fix
        points += GeoPoint(fix.lat, fix.lng)
        distanceMeters += step
        unsavedPoints += 1
        if (unsavedPoints >= SAVE_EVERY_POINTS) saveNow(scope)
        updateNotification()
    }

    private fun saveNow(target: CoroutineScope) {
        val syncId = walkSyncId ?: return
        val copy = points.toList()
        val distance = distanceMeters
        unsavedPoints = 0
        target.launch { repository.saveProgress(syncId, copy, distance) }
    }

    /** 끝냅니다. 시작이 아직 DB에 닿지 않았으면 기다렸다가 마무리합니다 — 시작하자마자 눌러도 걷기가 반쯤 남지 않게. */
    private fun stop() {
        if (stopping) return
        stopping = true
        removeUpdates()
        tickJob?.cancel()
        scope.launch {
            startJob?.join()
            val (syncId, copy, distance) = withContext(Dispatchers.Main) { Triple(walkSyncId, points.toList(), distanceMeters) }
            if (syncId != null) {
                repository.finishWalk(syncId, copy, distance)
            } else {
                // 앱이 거둬진 뒤 남은 알림의 끝내기를 눌렀을 때입니다. 적어 둔 데까지로 마무리합니다.
                repository.finishInterrupted()
            }
            withContext(Dispatchers.Main) {
                _recording.value = false
                ServiceCompat.stopForeground(this@WalkRecordingService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun updateNotification() {
        if (stopping) return
        AppNotifications.updateWalkRecordingNotification(this, notification())
    }

    private fun notification() = AppNotifications.walkRecordingNotification(
        context = this,
        distanceMeters = distanceMeters.toInt(),
        elapsedMillis = if (startedAt > 0L) System.currentTimeMillis() - startedAt else 0L,
        stopIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            Intent(this, WalkRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    )

    companion object {
        private const val ACTION_START = "com.codex.appgoodwords.action.WALK_START"
        private const val ACTION_STOP = "com.codex.appgoodwords.action.WALK_STOP"
        private const val STOP_REQUEST_CODE = 9_012

        /** 사람 걸음에 맞춘 간격입니다. 더 자주 받아도 [WalkPath.MIN_STEP_METERS] 안쪽은 버려집니다. */
        private const val INTERVAL_MILLIS = 2_000L

        /** 이만큼 점이 쌓이면 DB에 이어 적습니다. 서비스가 죽어도 잃는 길은 이만큼입니다. */
        private const val SAVE_EVERY_POINTS = 10
        private const val TICK_MILLIS = 30_000L

        private val _recording = MutableStateFlow(false)

        /** 지금 걷기를 기록하는 중인지. 화면의 시작·끝내기 단추가 봅니다. 앱 프로세스가 새로 뜨면 false입니다. */
        val recording: StateFlow<Boolean> = _recording.asStateFlow()

        fun hasLocationPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** 화면에서 부릅니다. 위치 권한은 부르는 쪽이 먼저 받아 둡니다. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WalkRecordingService::class.java).setAction(ACTION_START)
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, WalkRecordingService::class.java).setAction(ACTION_STOP))
        }
    }
}
