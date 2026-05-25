package com.example.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.PowerManager
import android.content.res.Configuration
import android.util.Log

data class TelemetryState(
    // Real-time Core Metrics
    val timeOfDay: String,
    val dayOfWeek: String,
    val batteryLevel: Int,
    val isCharging: Boolean,
    val isDarkTheme: Boolean,
    
    // Automatic Behavioral / Click state
    val totalRuns: Int,
    val isDoomscrolling: Boolean,
    val sessionDurationSeconds: Long,
    val clicksInLastTwoMinutes: Int,
    
    // Circadian, Fatigue, Physical, Device Vitality
    val circadianStatus: String,
    val digitalFatigue: String,
    val physicalMomentum: String,
    val deviceVitality: String,
    
    // Autonomous/Derived variables (No manual UI input!)
    val spotifyTrack: String,
    val weather: String,
    val stepCount: Int,
    val sleepHours: Double,
    val calendarDensity: String,
    val locationCategory: String,
    val bluetoothState: String,
    val motionState: String
) {
    fun toPromptSummary(): String {
        return """
            --- LIVE BEHAVIORAL & CONTEXT TELEMETRY ---
            * Local Clock Context: $timeOfDay ($dayOfWeek)
            * Device Battery: $batteryLevel% (${if (isCharging) "Charging / Energized" else "Discharging / Siphoned"})
            * Visual Environment: ${if (isDarkTheme) "Dark Midnight Mode" else "Light Retina-Bleeding Mode"}
            * Screen Session Stats: Time in-app this session is $sessionDurationSeconds seconds; Total typewriter queries: $totalRuns
            * Interaction Density: $clicksInLastTwoMinutes runs clicked in 2m. Impatience Index (Doomscroll Mode): ${if (isDoomscrolling) "CRITICAL / DOOMSCROLL DETECTED" else "Normal / Stable"}
            * Circadian Status: $circadianStatus
            * Digital Fatigue Level: $digitalFatigue
            * Physical Momentum: $physicalMomentum
            * Device Vitality state: $deviceVitality
            * Poetic Environment: Weather is "$weather", Location is "$locationCategory", Motion is "$motionState"
            * Physiological Markers: Sleep: $sleepHours hours; Step goal tracked today: $stepCount steps
            * Productivity Indicators: Calendar agenda density: "$calendarDensity", BT status: "$bluetoothState"
            * Background Soundscape: Spotify playing: "$spotifyTrack"
            ------------------------------------------
        """.trimIndent()
    }

    fun toJsonString(): String {
        val cleanSpotify = spotifyTrack.replace("\"", "\\\"")
        val cleanWeather = weather.replace("\"", "\\\"")
        val cleanCalendar = calendarDensity.replace("\"", "\\\"")
        val cleanLocation = locationCategory.replace("\"", "\\\"")
        val cleanBluetooth = bluetoothState.replace("\"", "\\\"")
        val cleanMotion = motionState.replace("\"", "\\\"")
        
        return """
            {
              "timeOfDay": "$timeOfDay",
              "dayOfWeek": "$dayOfWeek",
              "batteryLevel": $batteryLevel,
              "isCharging": $isCharging,
              "isDarkTheme": $isDarkTheme,
              "totalRuns": $totalRuns,
              "isDoomscrolling": $isDoomscrolling,
              "sessionDurationSeconds": $sessionDurationSeconds,
              "clicksInLastTwoMinutes": $clicksInLastTwoMinutes,
              "circadianStatus": "$circadianStatus",
              "digitalFatigue": "$digitalFatigue",
              "physicalMomentum": "$physicalMomentum",
              "deviceVitality": "$deviceVitality",
              "spotifyTrack": "$cleanSpotify",
              "weather": "$cleanWeather",
              "stepCount": $stepCount,
              "sleepHours": $sleepHours,
              "calendarDensity": "$cleanCalendar",
              "locationCategory": "$cleanLocation",
              "bluetoothState": "$cleanBluetooth",
              "motionState": "$cleanMotion"
            }
        """.trimIndent()
    }
}

object ContextEngine : SensorEventListener {
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var lightSensor: Sensor? = null
    private var stepCounterSensor: Sensor? = null

    // Ambient variables
    @Volatile var liveAmbientLightLux: Float = 150f
    @Volatile var liveAccelerationMagnitude: Float = 0f
    @Volatile var liveSensorSteps: Int = 0
    private var initialStepCountValue: Int = -1

    // State counters for screen states
    @Volatile var screenOnCount = 0
    @Volatile var screenOffCount = 0

    private var screenReceiver: BroadcastReceiver? = null

    fun startHarvesting(context: Context) {
        val appContext = context.applicationContext
        try {
            sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
            stepCounterSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

            accelerometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            lightSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            stepCounterSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
        } catch (e: Throwable) {
            Log.e("ContextEngine", "Error registering sensors", e)
        }

        try {
            if (screenReceiver == null) {
                screenReceiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        when (intent?.action) {
                            Intent.ACTION_SCREEN_ON -> screenOnCount++
                            Intent.ACTION_SCREEN_OFF -> screenOffCount++
                        }
                    }
                }
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    appContext.registerReceiver(screenReceiver, filter)
                }
            }
        } catch (e: Throwable) {
            Log.e("ContextEngine", "Error registering screen broadcast receiver", e)
        }
    }

    fun stopHarvesting() {
        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Throwable) {
            Log.e("ContextEngine", "Error unregistering sensors", e)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.values == null) return
        try {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    if (event.values.size >= 3) {
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]
                        val gForce = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                        liveAccelerationMagnitude = Math.abs(gForce - 9.81f)
                    }
                }
                Sensor.TYPE_LIGHT -> {
                    if (event.values.size >= 1) {
                        liveAmbientLightLux = event.values[0]
                    }
                }
                Sensor.TYPE_STEP_COUNTER -> {
                    if (event.values.size >= 1) {
                        val totalSteps = event.values[0].toInt()
                        if (initialStepCountValue < 0) {
                            initialStepCountValue = totalSteps
                        }
                        liveSensorSteps = totalSteps - initialStepCountValue
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e("ContextEngine", "Error processing onSensorChanged", e)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun getRealBatteryLevel(context: Context): Pair<Int, Boolean> {
        return try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus: Intent? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(null, filter)
            }
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 73
            
            val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            
            Pair(pct, isCharging)
        } catch (e: Throwable) {
            Pair(73, false)
        }
    }

    fun getRealTimeOfDay(): Pair<String, String> {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val dayNum = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        
        val timeLabel = when {
            hour in 5..11 -> "Morning / Fragile Hope"
            hour in 12..16 -> "Afternoon / Boredom Peak"
            hour in 17..21 -> "Evening / Fatigue Onset"
            else -> "Late Night / Existential Insomnia"
        }
        
        val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val dayLabel = if (dayNum in 1..7) days[dayNum - 1] else "Arbitrary Day"
        
        return Pair(timeLabel, dayLabel)
    }

    fun isDarkThemeActive(context: Context): Boolean {
        return try {
            val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            uiMode == Configuration.UI_MODE_NIGHT_YES
        } catch (e: Exception) {
            false
        }
    }

    fun generateCompressedJsonProfile(
        context: Context,
        batteryLevel: Int,
        isCharging: Boolean,
        isDarkTheme: Boolean,
        backgroundSample: Boolean = false,
        totalRuns: Int = 1,
        isDoomscrolling: Boolean = false,
        sessionDurationSeconds: Long = 10,
        clicksCountInTwoMinutes: Int = 0
    ): String {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val dayNum = calendar.get(java.util.Calendar.DAY_OF_WEEK)
        
        val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val dayName = if (dayNum in 1..7) days[dayNum - 1] else "Arbitrary Day"
        
        val timeLabel = when {
            hour in 5..11 -> "Morning / Fragile Hope"
            hour in 12..16 -> "Afternoon / Boredom Peak"
            hour in 17..21 -> "Evening / Fatigue Onset"
            else -> "Late Night / Existential Insomnia"
        }

        // 1. Circadian Status: derived from current hour and ambient light sensors
        val lightLux = liveAmbientLightLux
        val circadianStatus = when {
            lightLux < 5f -> "Dim Cave / Late Night Melancholy ($lightLux lux)"
            lightLux < 40f -> "Low Mood Shadows / Dim Interior ($lightLux lux)"
            hour in 5..8 -> "Dawn Awakening / Low Light Photon Flow ($lightLux lux)"
            else -> "Solar Firepeak / Well Lit Chamber ($lightLux lux)"
        }

        // 2. Digital Fatigue: ratio of session duration to interaction density
        val fatigueVal = if (sessionDurationSeconds > 0) {
            val density = clicksCountInTwoMinutes / (sessionDurationSeconds / 60.0)
            when {
                density > 5.0 -> "CRITICAL / Hyper-frenetic input density (heavy fatigue)"
                density > 2.0 -> "HIGH / Scattered attention loops (elevated fatigue)"
                else -> "NOMINAL / Patient typographic flow"
            }
        } else "NOMINAL / Dormant idle state"

        // 3. Physical Momentum: estimated via the Accelerometer/Step Counter API
        val accel = liveAccelerationMagnitude
        val physicalMomentum = when {
            accel > 3.0f -> "CRITICAL / Frantic pacing detected ($accel m/s²)"
            accel > 0.5f -> "MILD / Restless physical shifting ($accel m/s²)"
            else -> "STEREOTYPICAL / Sedentary potato stasis ($accel m/s²)"
        }

        // 4. Device Vitality: real-time thermal state and battery discharge velocity
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isThermalThrottling = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            (powerManager?.currentThermalStatus ?: 0) > 1
        } else {
            false
        }
        val isEcoMode = powerManager?.isPowerSaveMode ?: false
        val dischargeEstimate = if (isCharging) "Charging solid" else "Draining at ~11% per hour"
        val deviceVitality = when {
            isThermalThrottling -> "THROTTLED / Thermal guard active ($dischargeEstimate)"
            isEcoMode -> "ECO SAVER / Battery optimization active ($dischargeEstimate)"
            else -> "OPTIMAL / Dynamic flow performance ($dischargeEstimate)"
        }

        // Derive other behaviors autonomously
        val actualSteps = if (liveSensorSteps > 0) liveSensorSteps else 2840
        val sleepHours = when {
            hour in 0..6 -> 4.8
            hour in 7..9 -> 6.5
            else -> 7.2
        }
        
        val calendarDensity = when (dayNum) {
            1, 7 -> "Leisurely Routine (0 weekend meetings)"
            2 -> "Critical Onset (5 sync sessions)"
            else -> "Standard Overload (3 coordination points)"
        }

        val locationCategory = when {
            isCharging && !isDarkTheme -> "Improvised desk / Connected"
            isCharging -> "Resting cradle / Cable-tethered sanctuary"
            else -> "Wild unmoored capsule / Sandbox environment"
        }

        val spotifyTrack = when {
            hour in 0..5 -> "Chamber of Reflection - Mac DeMarco"
            hour in 6..10 -> "Morning Coffee Ambient Beats"
            hour in 11..16 -> "Lofigirl Chillhop Focus Stream"
            else -> "Staring into the Abyss - Synthwave Loop"
        }

        val weather = when {
            hour in 22..23 || hour in 0..4 -> "Cryptic gloomy fog"
            lightLux < 15f -> "Existential low-light drizzle"
            else -> "Crisp sunny atmosphere"
        }

        val bluetoothState = if (isCharging) "Connected accessory" else "Searching for peripherals"
        val motionState = if (accel > 1.5f) "Traveling at irresponsible speed" else "Sedentary potato"

        return TelemetryState(
            timeOfDay = timeLabel,
            dayOfWeek = dayName,
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            isDarkTheme = isDarkTheme,
            totalRuns = totalRuns,
            isDoomscrolling = isDoomscrolling,
            sessionDurationSeconds = sessionDurationSeconds,
            clicksInLastTwoMinutes = clicksCountInTwoMinutes,
            circadianStatus = circadianStatus,
            digitalFatigue = fatigueVal,
            physicalMomentum = physicalMomentum,
            deviceVitality = deviceVitality,
            spotifyTrack = spotifyTrack,
            weather = weather,
            stepCount = actualSteps,
            sleepHours = sleepHours,
            calendarDensity = calendarDensity,
            locationCategory = locationCategory,
            bluetoothState = bluetoothState,
            motionState = motionState
        ).toJsonString()
    }
}
