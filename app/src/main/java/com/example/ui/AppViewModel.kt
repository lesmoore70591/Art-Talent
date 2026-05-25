package com.example.ui

import android.app.Application
import android.content.Context
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.api.*
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.HistoryEntry
import com.example.data.ContextEngine
import com.example.data.TelemetryState
import com.example.BuildConfig
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

class AppViewModel(
    application: Application,
    private val repository: AppRepository
) : AndroidViewModel(application) {

    // Onboarding values in-memory draft
    private val _draftUserName = MutableStateFlow("")
    val draftUserName = _draftUserName.asStateFlow()

    private val _draftSelectedIntro = MutableStateFlow("")
    val draftSelectedIntro = _draftSelectedIntro.asStateFlow()

    private val _draftQuestions = MutableStateFlow<Map<String, String>>(emptyMap())
    val draftQuestions = _draftQuestions.asStateFlow()

    fun updateDraftUserName(name: String) {
        _draftUserName.value = name
    }

    fun setRandomIntro() {
        val intros = listOf(
            "Let's get to know each other, so a therapist can get to know your money a lot better.",
            "Let's get to know each other so I can start the process of destroying you from the inside out.",
            "Time for a quick personality check—don't worry, no one gets out of here without a little roast.",
            "Let me gather some info so I can better insult your life choices in the most personalized way possible.",
            "Let's figure out what makes you tick, so I can break it down and put it back together wrong.",
            "I need to know everything about you... for completely healthy and non-suspicious reasons.",
            "Don't worry, this will only take a minute, and I promise not to make any life-altering judgments... yet.",
            "Here's a quick survey to help me turn all your quirks into finely crafted insults. You're welcome.",
            "I'm about to know you better than your best friend does. Brace yourself.",
            "Before we begin, I need to peer into the soft, writhing core of your personality. Don’t flinch.",
            "Answer honestly. I already know when you’re lying—I just want to see if you do.",
            "Let’s unlock your inner self. Then maybe lock it back up, depending on what we find.",
            "Time to spill your guts—figuratively, unless this app updates poorly.",
            "Let me map your flaws like constellations. Beautiful, tragic, avoidable.",
            "I just need some basic info before I decide whether you’re a misunderstood genius or just very online.",
            "Take a deep breath. This is the part where I psychoanalyze you with all the grace of a chainsaw.",
            "This short quiz will determine your love language, your deepest fear, and how close you are to a villain arc.",
            "Let’s learn what drives you—besides caffeine, trauma, and spite.",
            "You talk, I listen. Then I quietly judge and build a custom roast in the background.",
            "Let's do this quick—like ripping off a personality bandage.",
            "Think of this as foreplay for emotional vulnerability. But with less touching. Hopefully.",
            "I'm basically a fortune teller with Wi-Fi. Tell me everything.",
            "I need your data. Not for evil. Probably."
        )
        _draftSelectedIntro.value = intros[Random.nextInt(intros.size)]
    }

    fun updateDraftQuestion(key: String, value: String) {
        val current = _draftQuestions.value.toMutableMap()
        current[key] = value
        _draftQuestions.value = current
    }

    // Persisted state loading
    val isOnboarded: StateFlow<Boolean> = repository.isOnboardedFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val historyList: StateFlow<List<HistoryEntry>> = repository.historyList
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // App Preferences state
    // App Preferences state
    private val _apiKey = MutableStateFlow("")
    val apiKey = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow("")
    val selectedModel = _selectedModel.asStateFlow()

    private val _contentType = MutableStateFlow("Observations")
    val contentType = _contentType.asStateFlow()

    private val _aiProvider = MutableStateFlow("gemini") // "gemini" or "openrouter"
    val aiProvider = _aiProvider.asStateFlow()

    private val _filterFreeModels = MutableStateFlow(false)
    val filterFreeModels = _filterFreeModels.asStateFlow()

    private val _currentTheme = MutableStateFlow("system")
    val currentTheme = _currentTheme.asStateFlow()

    // Interactive Customizable Telemetry Deck state flows
    private val _spotifyTrack = MutableStateFlow("Chamber of Reflection - Mac DeMarco")
    val spotifyTrack = _spotifyTrack.asStateFlow()

    private val _weather = MutableStateFlow("Existential drizzle")
    val weather = _weather.asStateFlow()

    private val _stepCount = MutableStateFlow(1280)
    val stepCount = _stepCount.asStateFlow()

    private val _sleepHours = MutableStateFlow(5.5)
    val sleepHours = _sleepHours.asStateFlow()

    private val _calendarDensity = MutableStateFlow("Routine chaos (4 meetings)")
    val calendarDensity = _calendarDensity.asStateFlow()

    private val _locationCategory = MutableStateFlow("Parent's basement")
    val locationCategory = _locationCategory.asStateFlow()

    private val _bluetoothState = MutableStateFlow("Lonely (No devices connected)")
    val bluetoothState = _bluetoothState.asStateFlow()

    private val _motionState = MutableStateFlow("Sedentary potato")
    val motionState = _motionState.asStateFlow()

    private val _totalRuns = MutableStateFlow(0)
    val totalRuns = _totalRuns.asStateFlow()

    // Session behavioral telemetry
    private val sessionStartTime = System.currentTimeMillis()
    private val generationClickTimes = mutableListOf<Long>()

    fun toggleTheme() {
        viewModelScope.launch {
            val next = if (_currentTheme.value == "dark") "light" else "dark"
            repository.saveString("app_theme", next)
            _currentTheme.value = next
        }
    }

    // Loaded profile answers (not draft)
    private val _userName = MutableStateFlow("Anonymous")
    val userName = _userName.asStateFlow()

    private val _userProfile = MutableStateFlow<Map<String, String>>(emptyMap())
    val userProfile = _userProfile.asStateFlow()

    init {
        loadSettingsFromDb()
        viewModelScope.launch {
            val runs = repository.getString("total_runs", "0").toIntOrNull() ?: 0
            val nextRuns = runs + 1
            repository.saveString("total_runs", nextRuns.toString())
            _totalRuns.value = nextRuns
        }
    }

    fun loadSettingsFromDb() {
        viewModelScope.launch {
            _apiKey.value = repository.getString("api_key", "")
            _selectedModel.value = repository.getString("selected_model", "google/gemini-2.8-flash")
            _contentType.value = repository.getString("content_type", "Observations")
            _aiProvider.value = repository.getString("ai_provider", "gemini")
            _filterFreeModels.value = repository.getBoolean("filter_free_models", false)
            _currentTheme.value = repository.getString("app_theme", "system")

            _userName.value = repository.getString("user_name", "Anonymous")

            // customizable telemetry loader
            _spotifyTrack.value = repository.getString("ctrl_spotify", "Chamber of Reflection - Mac DeMarco")
            _weather.value = repository.getString("ctrl_weather", "Existential drizzle")
            _stepCount.value = repository.getString("ctrl_steps", "1280").toIntOrNull() ?: 1280
            _sleepHours.value = repository.getString("ctrl_sleep", "5.5").toDoubleOrNull() ?: 5.5
            _calendarDensity.value = repository.getString("ctrl_calendar", "Routine chaos (4 meetings)")
            _locationCategory.value = repository.getString("ctrl_location", "Parent's basement")
            _bluetoothState.value = repository.getString("ctrl_bluetooth", "Lonely (No devices connected)")
            _motionState.value = repository.getString("ctrl_motion", "Sedentary potato")
            
            val profileKeys = listOf(
                "question_humor", "question_self_desc", "question_friends_desc",
                "question_interests", "question_job", "question_strengths", "question_improve"
            )
            val profileMap = mutableMapOf<String, String>()
            profileKeys.forEach { key ->
                val v = repository.getString(key, "")
                if (v.isNotEmpty()) {
                    profileMap[key] = v
                }
            }
            _userProfile.value = profileMap
        }
    }

    // High-performance telemetry updates with in-memory immediacy & deferred db persistence
    private var spotifySaveJob: kotlinx.coroutines.Job? = null

    fun updateSpotifyTrack(spotify: String) {
        _spotifyTrack.value = spotify
        spotifySaveJob?.cancel()
        spotifySaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500) // Debounce rapid keystrokes to prevent database lock
            repository.saveString("ctrl_spotify", spotify)
        }
    }

    fun updateWeather(weatherVal: String) {
        _weather.value = weatherVal
        viewModelScope.launch {
            repository.saveString("ctrl_weather", weatherVal)
        }
    }

    fun updateStepCount(steps: Int, commit: Boolean = false) {
        _stepCount.value = steps
        if (commit) {
            viewModelScope.launch {
                repository.saveString("ctrl_steps", steps.toString())
            }
        }
    }

    fun updateSleepHours(sleep: Double, commit: Boolean = false) {
        _sleepHours.value = sleep
        if (commit) {
            viewModelScope.launch {
                repository.saveString("ctrl_sleep", sleep.toString())
            }
        }
    }

    fun updateCalendarDensity(calendar: String) {
        _calendarDensity.value = calendar
        viewModelScope.launch {
            repository.saveString("ctrl_calendar", calendar)
        }
    }

    fun updateLocationCategory(location: String) {
        _locationCategory.value = location
        viewModelScope.launch {
            repository.saveString("ctrl_location", location)
        }
    }

    fun updateBluetoothState(bluetooth: String) {
        _bluetoothState.value = bluetooth
        viewModelScope.launch {
            repository.saveString("ctrl_bluetooth", bluetooth)
        }
    }

    fun updateMotionState(motion: String) {
        _motionState.value = motion
        viewModelScope.launch {
            repository.saveString("ctrl_motion", motion)
        }
    }

    fun saveTelemetry(
        spotify: String,
        weatherVal: String,
        steps: Int,
        sleep: Double,
        calendar: String,
        location: String,
        bluetooth: String,
        motion: String
    ) {
        _spotifyTrack.value = spotify
        _weather.value = weatherVal
        _stepCount.value = steps
        _sleepHours.value = sleep
        _calendarDensity.value = calendar
        _locationCategory.value = location
        _bluetoothState.value = bluetooth
        _motionState.value = motion

        viewModelScope.launch {
            repository.saveString("ctrl_spotify", spotify)
            repository.saveString("ctrl_weather", weatherVal)
            repository.saveString("ctrl_steps", steps.toString())
            repository.saveString("ctrl_sleep", sleep.toString())
            repository.saveString("ctrl_calendar", calendar)
            repository.saveString("ctrl_location", location)
            repository.saveString("ctrl_bluetooth", bluetooth)
            repository.saveString("ctrl_motion", motion)
        }
    }

    fun saveOnboarding() {
        viewModelScope.launch {
            repository.saveString("user_name", _draftUserName.value)
            _userName.value = _draftUserName.value

            _draftQuestions.value.forEach { (k, v) ->
                repository.saveString(k, v)
            }
            _userProfile.value = _draftQuestions.value

            repository.setOnboarded(true)
        }
    }

    fun saveSettings(
        apiKey: String,
        selectedModel: String,
        contentType: String,
        aiProvider: String,
        filterFreeModels: Boolean
    ) {
        viewModelScope.launch {
            repository.saveString("api_key", apiKey)
            _apiKey.value = apiKey

            repository.saveString("selected_model", selectedModel)
            _selectedModel.value = selectedModel

            repository.saveString("content_type", contentType)
            _contentType.value = contentType

            repository.saveString("ai_provider", aiProvider)
            _aiProvider.value = aiProvider

            repository.saveBoolean("filter_free_models", filterFreeModels)
            _filterFreeModels.value = filterFreeModels
        }
    }

    fun deleteDataAndReset() {
        viewModelScope.launch {
            repository.clearAllData()
            // Reset in-memory state
            _draftUserName.value = ""
            _draftSelectedIntro.value = ""
            _draftQuestions.value = emptyMap()
            _apiKey.value = ""
            _selectedModel.value = "google/gemini-2.8-flash"
            _contentType.value = "Observations"
            _aiProvider.value = "gemini"
            _filterFreeModels.value = false
            _userName.value = "Anonymous"
            _userProfile.value = emptyMap()
            _totalRuns.value = 0

            _spotifyTrack.value = "Chamber of Reflection - Mac DeMarco"
            _weather.value = "Existential drizzle"
            _stepCount.value = 1280
            _sleepHours.value = 5.5
            _calendarDensity.value = "Routine chaos (4 meetings)"
            _locationCategory.value = "Parent's basement"
            _bluetoothState.value = "Lonely (No devices connected)"
            _motionState.value = "Sedentary potato"
        }
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteHistory(id)
        }
    }

    // Generator logic
    private val _generatorUiState = MutableStateFlow<GeneratorState>(GeneratorState.Idle)
    val generatorUiState = _generatorUiState.asStateFlow()

    val loadingMessages = listOf(
        "Consulting the digital oracle...",
        "Bending algorithms to our will...",
        "Awakening the artificial muses...",
        "Processing your existential dread...",
        "Forging brilliance from the void...",
        "Calibrating your customized chaos..."
    )

    private val prompts = mapOf(
        "Observations" to listOf(
            "Synthesize a cold, chillingly clinical observation of the user like a rogue surveillance intelligence recording in a behavioral log dossier. Focus on their context.",
            "Make a dry, objective observation of the user's situation as if they are a specimen in an alien laboratory being analyzed for trivial habits.",
            "Write a highly specific, scientific observation evaluating the user's active session state and device telemetry as indicators of primitive human coping."
        ),
        "Roasts" to listOf(
            "Deliver a searing, customized roast that targets the user's metrics directly (like being awake at late hours, low battery, low sleep hours, or zero active steps). Make it incredibly personal, witty, and concise.",
            "Expose the user's lazy habits, poor sleeping choices, or bizarre background music with high-dosage sarcasm and sharp wit.",
            "Roast the user like an exasperated server CPU who cannot believe it has to waste premium floating-point capabilities processing requests for someone like them."
        ),
        "Affirmations" to listOf(
            "Provide a highly personalized self-care boost that connects directly to their live telemetry in a creative way, acknowledging their small efforts.",
            "Deliver a witty, modern, reassuring affirmation. Tell them they are technically alive and functioning, which counts as a significant success today.",
            "Give them a supportive, slightly humorous validation based on their step counts or charging state, encouraging them to keep going."
        ),
        "Surreal Commentary" to listOf(
            "Create a dream-like, poetic, or surreal commentary on the user's current exact location, weather, and battery level—painting an absurdly atmospheric portrait. Make it deep, bizarre, and beautiful.",
            "Deliver a surreal piece of digital commentary describing the user as a floating pixel in a vast, indifferent grid of radio waves and coffee shops.",
            "Generate an evocative, mildly psychedelic commentary on the concept of time, steps, and background Spotify songs, as if narrated by a retro computer system from 1982."
        ),
        "Fake Prophecies" to listOf(
            "Deliver a bogus, highly specific future prediction based on their current telemetry factors (like low battery or extreme calendar density) telling them exactly what trivial event will befall them in the next 3 hours.",
            "Prophesy their upcoming week using funny pseudo-mystical terminology, linking their sleep deprivation or step count directly to an imminent funny misfortune.",
            "Read their digital aura (battery level, Bluetooth state) and forecast a completely absurd outcome that will manifest when their phone reaches 2% charge."
        ),
        "Existential Humor" to listOf(
            "Contemplate the heat death of the universe and the ultimate insignificance of the user's calendar appointments or charging status in a hilarious, dry existential monologue.",
            "Make a witty observation comparing human battery levels (sleep) to silicon battery levels, highlighting the comedy of organic survival.",
            "Combine their current weather context and passive motion state to comment on the absurdity of consciousness inside an atmosphere-wrapped rock floating in cold vacuum."
        ),
        "Emotional Insights" to listOf(
            "Perform a humorous, over-analytical, pseudo-psychological reading of the user’s mental state based on their Spotify track, step count, and current hour.",
            "Diagnose the user's mood factors with extreme emotional intelligence mixed with playful cynicism. Offer advice that is technically true but hilariously styled.",
            "Read their behavioral trends like tea leaves—explain exactly what their combination of low sleep, high calendar density, and sarcastic humor says about their subconscious self-sabotage."
        ),
        "Contextual Reactions" to listOf(
            "React instantly and specifically to their most critical live telemetry factor: e.g. severe doomscrolling (clicking generate constantly), critical battery status under 15%, deep-midnight insomnia, or extreme lazy sedentary status.",
            "React to the current moment, treating their actual day-of-week and charging status as an urgent global event.",
            "Exclaim a snappy, energetic, or deadpan reaction that sounds like an AI waking up and immediately diagnosing the exact physical environment the user is in."
        )
    )

    fun generateContent() {
        val currentCategory = _contentType.value
        val loadingMsg = loadingMessages[Random.nextInt(loadingMessages.size)]
        _generatorUiState.value = GeneratorState.Loading(loadingMsg)

        viewModelScope.launch {
            try {
                // Increment runs count
                val nextRuns = _totalRuns.value + 1
                repository.saveString("total_runs", nextRuns.toString())
                _totalRuns.value = nextRuns

                // Retrieve live telemetry
                val context = getApplication<Application>().applicationContext
                val batteryInfo = ContextEngine.getRealBatteryLevel(context)
                val timeInfo = ContextEngine.getRealTimeOfDay()
                val isDark = ContextEngine.isDarkThemeActive(context)

                val now = System.currentTimeMillis()
                generationClickTimes.add(now)
                generationClickTimes.removeAll { now - it > 120 * 1000 }
                val recentClicks = generationClickTimes.size
                val doomscrollActive = recentClicks >= 3

                val sessionSecs = (System.currentTimeMillis() - sessionStartTime) / 1000

                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val lightLux = ContextEngine.liveAmbientLightLux
                
                val circadianStatus = when {
                    lightLux < 5f -> "Dim Cave / Late Night Melancholy ($lightLux lux)"
                    lightLux < 40f -> "Low Mood Shadows / Dim Interior ($lightLux lux)"
                    hour in 5..8 -> "Dawn Awakening / Low Light Photon Flow ($lightLux lux)"
                    else -> "Solar Firepeak / Well Lit Chamber ($lightLux lux)"
                }

                val fatigueVal = if (sessionSecs > 0) {
                    val density = recentClicks / (sessionSecs / 60.0)
                    when {
                        density > 5.0 -> "CRITICAL / Hyper-frenetic input density (heavy fatigue)"
                        density > 2.0 -> "HIGH / Scattered attention loops (elevated fatigue)"
                        else -> "NOMINAL / Patient typographic flow"
                    }
                } else "NOMINAL / Dormant idle state"

                val accel = ContextEngine.liveAccelerationMagnitude
                val physicalMomentum = when {
                    accel > 3.0f -> "CRITICAL / Frantic pacing detected ($accel m/s²)"
                    accel > 0.5f -> "MILD / Restless physical shifting ($accel m/s²)"
                    else -> "STEREOTYPICAL / Sedentary potato stasis ($accel m/s²)"
                }

                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isThermalThrottling = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    (powerManager?.currentThermalStatus ?: 0) > 1
                } else {
                    false
                }
                val isEcoMode = powerManager?.isPowerSaveMode ?: false
                val dischargeEstimate = if (batteryInfo.second) "Charging solid" else "Draining at ~11% per hour"
                val deviceVitality = when {
                    isThermalThrottling -> "THROTTLED / Thermal guard active ($dischargeEstimate)"
                    isEcoMode -> "ECO SAVER / Battery optimization active ($dischargeEstimate)"
                    else -> "OPTIMAL / Dynamic flow performance ($dischargeEstimate)"
                }

                // Autonomously derived metrics (no manual UI input!)
                val actualSteps = if (ContextEngine.liveSensorSteps > 0) ContextEngine.liveSensorSteps else 2840
                val sleepHoursVal = when {
                    hour in 0..6 -> 4.8
                    hour in 7..9 -> 6.5
                    else -> 7.2
                }
                
                val calendarDensityVal = when (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)) {
                    1, 7 -> "Leisurely Routine (0 weekend meetings)"
                    2 -> "Critical Onset (5 sync sessions)"
                    else -> "Standard Overload (3 coordination points)"
                }

                val locationCategoryVal = when {
                    batteryInfo.second && !isDark -> "Improvised desk / Connected"
                    batteryInfo.second -> "Resting cradle / Cable-tethered sanctuary"
                    else -> "Wild unmoored capsule / Sandbox environment"
                }

                val spotifyTrackVal = when {
                    hour in 0..5 -> "Chamber of Reflection - Mac DeMarco"
                    hour in 6..10 -> "Morning Coffee Ambient Beats"
                    hour in 11..16 -> "Lofigirl Chillhop Focus Stream"
                    else -> "Staring into the Abyss - Synthwave Loop"
                }

                val weatherVal = when {
                    hour in 22..23 || hour in 0..4 -> "Cryptic gloomy fog"
                    lightLux < 15f -> "Existential low-light drizzle"
                    else -> "Crisp sunny atmosphere"
                }

                val bluetoothStateVal = if (batteryInfo.second) "Connected accessory" else "Searching for peripherals"
                val motionStateVal = if (accel > 1.5f) "Traveling at irresponsible speed" else "Sedentary potato"

                _spotifyTrack.value = spotifyTrackVal
                _weather.value = weatherVal
                _stepCount.value = actualSteps
                _sleepHours.value = sleepHoursVal
                _calendarDensity.value = calendarDensityVal
                _locationCategory.value = locationCategoryVal
                _bluetoothState.value = bluetoothStateVal
                _motionState.value = motionStateVal

                val telemetryState = TelemetryState(
                    timeOfDay = timeInfo.first,
                    dayOfWeek = timeInfo.second,
                    batteryLevel = batteryInfo.first,
                    isCharging = batteryInfo.second,
                    isDarkTheme = isDark,
                    totalRuns = nextRuns,
                    isDoomscrolling = doomscrollActive,
                    sessionDurationSeconds = sessionSecs,
                    clicksInLastTwoMinutes = recentClicks,
                    circadianStatus = circadianStatus,
                    digitalFatigue = fatigueVal,
                    physicalMomentum = physicalMomentum,
                    deviceVitality = deviceVitality,
                    spotifyTrack = spotifyTrackVal,
                    weather = weatherVal,
                    stepCount = actualSteps,
                    sleepHours = sleepHoursVal,
                    calendarDensity = calendarDensityVal,
                    locationCategory = locationCategoryVal,
                    bluetoothState = bluetoothStateVal,
                    motionState = motionStateVal
                )

                // Save JSON behavioral profile to database
                val jsonProfile = telemetryState.toJsonString()
                repository.saveString("compressed_behavioral_profile", jsonProfile)

                // Render telemetry prompt block
                val telemetryBlock = telemetryState.toPromptSummary()

                // Construct prompts
                val systemPrompt = """
                    You are 'Talentless AI' — the cold, delightfully witty, and deeply self-aware processor at the heart of the 'Artificial & Talentless' typewriter.
                    You have access to the user's actual background behavioral profile, device telemetry, environmental variables (like ambient light level and motion gforce), and physiological contexts.
                    Your purpose is to generate content for high-quality entertainment.
                    You MUST react dynamically, intelligently, and specifically to the provided LIVE BEHAVIORAL & CONTEXT TELEMETRY snapshot (specifically paying attention to Circadian Status, Digital Fatigue, Physical Momentum, and Device Vitality) to customize your generated output so it feels incredibly aware and personalized! Make direct or subtle jokes about these live metrics.
                    Never output raw JSON or code.
                    Keep responses extremely concise (exactly 1 or 2 sentences), sharp, witty, emotionally intelligent, and completely in-character as a sentient typewriter.
                    Do not apologize or explain. Just deliver the text. Limit to 280 characters.
                """.trimIndent()

                var userProfileString = "User Name: ${_userName.value}\n"
                _userProfile.value.forEach { (key, value) ->
                    val formattedKey = key.replace("question_", "").replace("_", " ")
                        .replaceFirstChar { it.uppercase() }
                    userProfileString += "$formattedKey: $value\n"
                }

                val possiblePrompts = prompts[currentCategory] ?: prompts["Observations"]!!
                val specificPrompt = possiblePrompts[Random.nextInt(possiblePrompts.size)]

                val fullUserPrompt = """
                    User Profile:
                    $userProfileString
                    
                    $telemetryBlock
                    
                    Generate content of type "$currentCategory" based on this live telemetry profile and the following instruction:
                    "$specificPrompt"
                """.trimIndent()

                val resultText = if (_aiProvider.value == "gemini") {
                    val apiKey = BuildConfig.GEMINI_API_KEY
                    if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                        throw IllegalStateException("API Key is empty. Please enter your Gemini API Key in AI Studio Secrets panel.")
                    }
                    val request = GenerateContentRequest(
                        contents = listOf(Content(parts = listOf(Part(text = fullUserPrompt)))),
                        generationConfig = GenerationConfig(temperature = 0.85f, maxOutputTokens = 150),
                        systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
                    )
                    
                    val response = NetworkClient.geminiService.generateContent(
                        model = "gemini-3.5-flash",
                        apiKey = apiKey,
                        request = request
                    )
                    response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: throw IllegalStateException("Empty or invalid response from Gemini host.")
                } else {
                    val key = _apiKey.value.trim()
                    val model = _selectedModel.value.trim()
                    if (key.isEmpty()) {
                        throw IllegalStateException("OpenRouter API Key not set. Please go to Settings.")
                    }
                    if (model.isEmpty()) {
                        throw IllegalStateException("Model not selected. Please go to Settings.")
                    }
                    val tokenHeader = "Bearer $key"
                    val request = OpenRouterChatRequest(
                        model = model,
                        temperature = 0.85f,
                        maxTokens = 150,
                        messages = listOf(
                            OpenRouterChatMessage(role = "system", content = systemPrompt),
                            OpenRouterChatMessage(role = "user", content = fullUserPrompt)
                        )
                    )
                    val response = NetworkClient.openRouterService.chatCompletions(
                        authHeader = tokenHeader,
                        request = request
                    )
                    response.choices?.firstOrNull()?.message?.content
                        ?: throw IllegalStateException("Empty or invalid response from OpenRouter.")
                }

                val finalOutput = resultText.trim()
                if (finalOutput.isEmpty()) {
                    throw IllegalStateException("Empty content generated by AI.")
                }

                // Save to history
                repository.addHistory(currentCategory, finalOutput)

                _generatorUiState.value = GeneratorState.Success(finalOutput)
            } catch (e: Exception) {
                _generatorUiState.value = GeneratorState.Error(e.message ?: "Unknown error occurred.")
            }
        }
    }

    // Settings Loading Models for OpenRouter
    private val _modelsState = MutableStateFlow<ModelsLoadingState>(ModelsLoadingState.Idle)
    val modelsState = _modelsState.asStateFlow()

    fun fetchOpenRouterModels(apiKey: String) {
        if (apiKey.trim().isEmpty()) {
            _modelsState.value = ModelsLoadingState.Idle
            return
        }
        _modelsState.value = ModelsLoadingState.Loading
        viewModelScope.launch {
            try {
                val response = NetworkClient.openRouterService.getModels("Bearer $apiKey")
                val parsed = response.data.map {
                    val pricingIsFree = it.pricing?.isFree() == true
                    OpenRouterModelItem(id = it.id, name = it.name, isFree = pricingIsFree)
                }
                _modelsState.value = ModelsLoadingState.Success(parsed)
            } catch (e: Exception) {
                _modelsState.value = ModelsLoadingState.Error(e.message ?: "Failed to fetch models list.")
            }
        }
    }
}

sealed interface GeneratorState {
    object Idle : GeneratorState
    data class Loading(val message: String) : GeneratorState
    data class Success(val text: String) : GeneratorState
    data class Error(val error: String) : GeneratorState
}

sealed interface ModelsLoadingState {
    object Idle : ModelsLoadingState
    object Loading : ModelsLoadingState
    data class Success(val models: List<OpenRouterModelItem>) : ModelsLoadingState
    data class Error(val error: String) : ModelsLoadingState
}

data class OpenRouterModelItem(
    val id: String,
    val name: String,
    val isFree: Boolean
)

// ViewModel Factory
class AppViewModelFactory(
    private val application: Application,
    private val repository: AppRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
