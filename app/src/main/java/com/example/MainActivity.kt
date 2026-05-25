package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.OpenRouterModelItem
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.data.HistoryEntry
import com.example.ui.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RetroRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.platform.LocalContext
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = AppRepository(database)
        val factory = AppViewModelFactory(application, repository)
        val viewModel = ViewModelProvider(this, factory)[AppViewModel::class.java]

        com.example.data.ContextEngine.startHarvesting(applicationContext)
        com.example.data.TelemetryWorker.schedule(applicationContext)

        setContent {
            val themeMode by viewModel.currentTheme.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val useDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> systemDark
            }

            MyApplicationTheme(darkTheme = useDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppMainLayout(viewModel)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        com.example.data.ContextEngine.stopHarvesting()
    }
}

sealed interface AppScreen {
    object Welcome : AppScreen
    object NameInput : AppScreen
    object IntroLine : AppScreen
    data class Question(val pageIndex: Int) : AppScreen // 0 to 6
    object Generator : AppScreen
    object History : AppScreen
    object Settings : AppScreen
}

@Composable
fun AppMainLayout(viewModel: AppViewModel) {
    val isOnboarded by viewModel.isOnboarded.collectAsStateWithLifecycle()
    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Welcome) }
    val scope = rememberCoroutineScope()

    // State sync on start
    LaunchedEffect(isOnboarded) {
        currentScreen = if (isOnboarded) AppScreen.Generator else AppScreen.Welcome
    }

    when (currentScreen) {
        AppScreen.Welcome -> WelcomeScreen(
            onGetStarted = {
                currentScreen = AppScreen.NameInput
            }
        )
        AppScreen.NameInput -> NameInputScreen(
            viewModel = viewModel,
            onContinue = {
                viewModel.setRandomIntro()
                currentScreen = AppScreen.IntroLine
            }
        )
        AppScreen.IntroLine -> IntroLineScreen(
            viewModel = viewModel,
            onContinue = {
                currentScreen = AppScreen.Question(0)
            }
        )
        is AppScreen.Question -> {
            val page = (currentScreen as AppScreen.Question).pageIndex
            QuestionScreen(
                viewModel = viewModel,
                pageIndex = page,
                onPrev = {
                    if (page == 0) {
                        currentScreen = AppScreen.IntroLine
                    } else {
                        currentScreen = AppScreen.Question(page - 1)
                    }
                },
                onNext = {
                    if (page == 6) {
                        viewModel.saveOnboarding()
                        scope.launch {
                            delay(400) // smooth save transition
                            viewModel.loadSettingsFromDb()
                            currentScreen = AppScreen.Generator
                        }
                    } else {
                        currentScreen = AppScreen.Question(page + 1)
                    }
                }
            )
        }
        AppScreen.Generator -> GeneratorScreen(
            viewModel = viewModel,
            onGoToSettings = {
                viewModel.loadSettingsFromDb()
                currentScreen = AppScreen.Settings
            },
            onGoToHistory = {
                currentScreen = AppScreen.History
            }
        )
        AppScreen.History -> HistoryScreen(
            viewModel = viewModel,
            onBack = {
                currentScreen = AppScreen.Generator
            }
        )
        AppScreen.Settings -> SettingsScreen(
            viewModel = viewModel,
            onBack = {
                currentScreen = AppScreen.Generator
            }
        )
    }
}

@Composable
fun HeaderBar(
    title: String? = null,
    onThemeToggle: () -> Unit,
    onBack: (() -> Unit)? = null,
    onGoToHistory: (() -> Unit)? = null,
    onGoToSettings: (() -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .testTag("back_button")
                    .size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Spacer(modifier = Modifier.size(44.dp))
        }

        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                ),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        if (onGoToHistory != null && onGoToSettings != null) {
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .testTag("header_menu_button")
                        .size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(MaterialTheme.colorScheme.background)
                ) {
                    DropdownMenuItem(
                        text = { 
                            Text(
                                text = " [ SETTINGS ]", 
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                            ) 
                        },
                        onClick = {
                            menuExpanded = false
                            onGoToSettings()
                        }
                    )
                    DropdownMenuItem(
                        text = { 
                            Text(
                                text = " [ HISTORY ]", 
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                            ) 
                        },
                        onClick = {
                            menuExpanded = false
                            onGoToHistory()
                        }
                    )
                    DropdownMenuItem(
                        text = { 
                            Text(
                                text = " [ TOGGLE THEME ]", 
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                            ) 
                        },
                        onClick = {
                            menuExpanded = false
                            onThemeToggle()
                        }
                    )
                }
            }
        } else {
            IconButton(
                onClick = onThemeToggle,
                modifier = Modifier
                    .testTag("theme_toggle_button")
                    .size(44.dp)
            ) {
                val isDark = MaterialTheme.colorScheme.background != Color(0xFFFFFFE3)
                Icon(
                    imageVector = if (isDark) Icons.Default.WbSunny else Icons.Default.NightsStay,
                    contentDescription = "Toggle Theme",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    delayMillis: Long = 40,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
    textAlign: TextAlign = TextAlign.Center,
    onComplete: () -> Unit = {}
) {
    var displayedText by remember { mutableStateOf("") }
    LaunchedEffect(text) {
        displayedText = ""
        for (i in text.indices) {
            displayedText += text[i]
            delay(delayMillis + (if (Random.nextBoolean()) 20 else 0))
        }
        onComplete()
    }
    Text(
        text = displayedText,
        style = style,
        textAlign = textAlign,
        modifier = modifier
    )
}

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    var taglineComplete by remember { mutableStateOf(false) }
    var subtaglineComplete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Large display visual title logo (Brutal Typewriter) - kept stationary
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Artificial",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 38.sp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "—&—",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Talentless",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 38.sp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Tagline box matching index.html - fixed height prevents dynamic text-wrapping resizing from moving other elements
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                TypewriterText(
                    text = "An AI-powered minimalist typewriter generates personalized insults just for you.",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
                    textAlign = TextAlign.Center,
                    delayMillis = 35,
                    onComplete = { taglineComplete = true }
                )

                if (taglineComplete) {
                    Spacer(modifier = Modifier.height(20.dp))
                    TypewriterText(
                        text = "Enjoy. Or don't. Whatever.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                        textAlign = TextAlign.Center,
                        delayMillis = 50,
                        onComplete = { subtaglineComplete = true }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Button
        Button(
            onClick = onGetStarted,
            enabled = subtaglineComplete,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .testTag("get_started_button")
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .height(56.dp)
        ) {
            Text(
                text = "Get Started",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun NameInputScreen(
    viewModel: AppViewModel,
    onContinue: () -> Unit
) {
    val nameDraft by viewModel.draftUserName.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderBar(onThemeToggle = { viewModel.toggleTheme() })

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "What should I call you?",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            OutlinedTextField(
                value = nameDraft,
                onValueChange = { viewModel.updateDraftUserName(it) },
                placeholder = {
                    Text(
                        "Your name here...",
                        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.primary,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                modifier = Modifier
                    .testTag("name_input_field")
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            )
        }

        Button(
            onClick = onContinue,
            enabled = nameDraft.trim().isNotEmpty(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .testTag("name_continue_button")
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding()
                .height(56.dp)
        ) {
            Text(
                text = "Continue",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
fun IntroLineScreen(
    viewModel: AppViewModel,
    onContinue: () -> Unit
) {
    val introText by viewModel.draftSelectedIntro.collectAsStateWithLifecycle()
    var isTypewriterComplete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderBar(onThemeToggle = { viewModel.toggleTheme() })

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (introText.isNotEmpty()) {
                TypewriterText(
                    text = introText,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 30.sp),
                    textAlign = TextAlign.Center,
                    delayMillis = 30,
                    onComplete = { isTypewriterComplete = true }
                )
            }
        }

        Button(
            onClick = onContinue,
            enabled = isTypewriterComplete,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .testTag("intro_continue_button")
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding()
                .height(56.dp)
        ) {
            Text(
                text = "Continue",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

// Layout of multiple choice and open questions
@Composable
fun QuestionScreen(
    viewModel: AppViewModel,
    pageIndex: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val draftAnswers by viewModel.draftQuestions.collectAsStateWithLifecycle()

    val questionsList = listOf(
        "What type of humor do you enjoy?" to "question_humor",
        "If you had to choose one word to describe yourself, what would it be?" to "question_self_desc",
        "How would your friends describe your personality?" to "question_friends_desc",
        "What are some of your interests or hobbies?" to "question_interests",
        "If you are employed, describe your job. If not, describe a job you think you'd enjoy." to "question_job",
        "What do you consider your personal strengths?" to "question_strengths",
        "What are some areas you'd like to improve?" to "question_improve"
    )

    val currentQuestion = questionsList[pageIndex]
    val valueKey = currentQuestion.second
    val currentVal = draftAnswers[valueKey] ?: ""

    // Options for the multiple choice screens (index 0, 1, 2)
    val optionsList = when (pageIndex) {
        0 -> listOf("Sarcastic", "Dry", "Witty", "Goofy", "Dark", "Slapstick")
        1 -> listOf("Driven", "Laid-back", "Persistent", "Chaotic", "Curious", "Ambitious")
        2 -> listOf("Outgoing", "Chill", "The leader of the group", "The joker", "The thinker", "The quiet one")
        else -> emptyList()
    }

    val isNextEnabled = currentVal.trim().isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderBar(onThemeToggle = { viewModel.toggleTheme() })

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Page X of 7 label
            Text(
                text = "Page ${pageIndex + 1} of 7",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Question phrase
            Text(
                text = currentQuestion.first,
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 22.sp),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )

            if (pageIndex in 0..2) {
                // Interactive Grid for Multiple Choice standard values
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    optionsList.chunked(2).forEach { rowOptions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowOptions.forEach { opt ->
                                val isSelected = opt == currentVal
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("option_${opt.replace(" ", "_")}")
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.secondaryContainer
                                        )
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            viewModel.updateDraftQuestion(valueKey, opt)
                                        }
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = opt,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.primary
                                        ),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Open text area for open questions (3 to 6)
                OutlinedTextField(
                    value = currentVal,
                    onValueChange = { viewModel.updateDraftQuestion(valueKey, it) },
                    placeholder = {
                        Text(
                            "Type your response here...",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = false,
                    minLines = 4,
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Default
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.primary,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier
                        .testTag("open_ended_input")
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }
        }

        // Prev & Next actions row fixed at bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onPrev,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = BoxBorder(1.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .testTag("prev_button")
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text(
                    text = "Prev",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Button(
                onClick = onNext,
                enabled = isNextEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .testTag("next_button")
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text(
                    text = if (pageIndex == 6) "Finish" else "Next",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

// Extends helper to support Border configuration dynamically
@Composable
fun BoxBorder(width: androidx.compose.ui.unit.Dp, color: Color) = ButtonDefaults.outlinedButtonBorder.copy(
    width = width,
    brush = androidx.compose.ui.graphics.SolidColor(color)
)

@Composable
fun TelemetryDeck(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    val spotify by viewModel.spotifyTrack.collectAsStateWithLifecycle()
    val rWeather by viewModel.weather.collectAsStateWithLifecycle()
    val steps by viewModel.stepCount.collectAsStateWithLifecycle()
    val sleep by viewModel.sleepHours.collectAsStateWithLifecycle()
    val calendar by viewModel.calendarDensity.collectAsStateWithLifecycle()
    val location by viewModel.locationCategory.collectAsStateWithLifecycle()
    val bluetooth by viewModel.bluetoothState.collectAsStateWithLifecycle()
    val motion by viewModel.motionState.collectAsStateWithLifecycle()
    val runs by viewModel.totalRuns.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val (batteryPct, isCharging) = remember(runs) { com.example.data.ContextEngine.getRealBatteryLevel(context) }
    val (timeLabel, dayLabel) = remember(runs) { com.example.data.ContextEngine.getRealTimeOfDay() }
    val isDark = remember(runs) { com.example.data.ContextEngine.isDarkThemeActive(context) }

    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val lightLux = com.example.data.ContextEngine.liveAmbientLightLux
    val circadianStatus = when {
        lightLux < 5f -> "Dim Cave / Late Night Melancholy ($lightLux lux)"
        lightLux < 40f -> "Low Mood Shadows / Dim Interior ($lightLux lux)"
        hour in 5..8 -> "Dawn Awakening / Low Light Photon Flow ($lightLux lux)"
        else -> "Solar Firepeak / Well Lit Chamber ($lightLux lux)"
    }
    
    // Estimate session fatigue index based on click densities
    val fatigueVal = "NOMINAL / Patient typographic flow"
    val accel = com.example.data.ContextEngine.liveAccelerationMagnitude
    val physicalMomentum = when {
        accel > 3.0f -> "CRITICAL / Frantic pacing detected ($accel m/s²)"
        accel > 0.5f -> "MILD / Restless physical shifting ($accel m/s²)"
        else -> "STEREOTYPICAL / Sedentary potato stasis ($accel m/s²)"
    }
    val dischargeEstimate = if (isCharging) "Charging solid" else "Draining at ~11%/hr"
    val deviceVitality = "OPTIMAL / Dynamic flow performance ($dischargeEstimate)"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .clickable(
                    onClick = { isExpanded = !isExpanded },
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isExpanded) "[ HIDE ENVIRONMENT ]" else "[ SHOW ENVIRONMENT ]",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 2.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }

        androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = ". DIAGNOSTIC TERMINAL LOG",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "------------------------------------------",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )

                listOf(
                    Pair("CIRCADIAN", circadianStatus),
                    Pair("FATIGUE", fatigueVal),
                    Pair("MOMENTUM", physicalMomentum),
                    Pair("VITALITY", deviceVitality),
                    Pair("BATTERY", "$batteryPct% (${if (isCharging) "Charging" else "Discharging"})"),
                    Pair("CLOCK", "$dayLabel ($timeLabel)"),
                    Pair("AUDIO", spotify),
                    Pair("WEATHER", rWeather),
                    Pair("STEPS", "$steps steps today"),
                    Pair("SLEEP", "$sleep hours logged"),
                    Pair("LOCATION", location),
                    Pair("BLUETOOTH", bluetooth),
                    Pair("MOTION", motion)
                ).forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "  > $label: ",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun GeneratorScreen(
    viewModel: AppViewModel,
    onGoToSettings: () -> Unit,
    onGoToHistory: () -> Unit
) {
    val uiState by viewModel.generatorUiState.collectAsStateWithLifecycle()

    // Trigger initial generation on start if first load
    LaunchedEffect(Unit) {
        if (uiState is GeneratorState.Idle) {
            viewModel.generateContent()
        }
    }

    // Packaged Special Elite font family, loaded directly from resource with safety fallback
    val fontFamilyState = remember {
        try {
            FontFamily(
                Font(R.font.special_elite)
            )
        } catch (e: Exception) {
            FontFamily.Monospace
        }
    }

    var animationComplete by remember { mutableStateOf(false) }

    // Reset animation complete when text/state changes or is loading
    LaunchedEffect(uiState) {
        if (uiState is GeneratorState.Loading || uiState is GeneratorState.Idle) {
            animationComplete = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            GeneratorState.Idle -> {
                // Starts as a solid background
            }
            is GeneratorState.Loading -> {
                // Starts as a solid background with no distraction
            }
            is GeneratorState.Success -> {
                TypewriterText(
                    text = state.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = fontFamilyState,
                        fontSize = 18.sp,
                        lineHeight = 32.sp,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    textAlign = TextAlign.Center,
                    delayMillis = 40,
                    onComplete = {
                        animationComplete = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }
            is GeneratorState.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Connection paused.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = fontFamilyState,
                            color = RetroRed
                        )
                    )
                    Text(
                        text = "[ RETRY ]",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = fontFamilyState,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .clickable { viewModel.generateContent() }
                            .padding(8.dp)
                    )
                }
            }
        }

        // Settings icon on the top right
        // "only once the animation is complete should a small settings icon be reveal in the top right corner"
        if (animationComplete) {
            IconButton(
                onClick = onGoToSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(44.dp)
                    .testTag("settings_nav_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun HistoryScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val historyItems by viewModel.historyList.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderBar(
            title = "History Feed",
            onThemeToggle = { viewModel.toggleTheme() },
            onBack = onBack
        )

        if (historyItems.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Empty History",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    modifier = Modifier
                        .size(56.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No history recorded yet.\nTap 'Generate New' on the dashboard!",
                    style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(historyItems) { item ->
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.contentType,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                        RoundedCornerShape(4.dp)
                                    ).padding(horizontal = 6.dp, vertical = 2.dp)
                                )

                                IconButton(
                                    onClick = { viewModel.deleteHistoryItem(item.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = RetroRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.content,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val storedApiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val storedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val storedContentType by viewModel.contentType.collectAsStateWithLifecycle()
    val storedAiProvider by viewModel.aiProvider.collectAsStateWithLifecycle()
    val storedFilterFree by viewModel.filterFreeModels.collectAsStateWithLifecycle()
    val modelsState by viewModel.modelsState.collectAsStateWithLifecycle()

    var apiKeyDraft by remember { mutableStateOf(storedApiKey) }
    var providerDraft by remember { mutableStateOf(storedAiProvider) }
    var filterFreeDraft by remember { mutableStateOf(storedFilterFree) }
    var selectedModelDraft by remember { mutableStateOf(storedModel) }
    var selectedContentTypeDraft by remember { mutableStateOf(storedContentType) }

    var isResetConfirmOpen by remember { mutableStateOf(false) }

    // Synchronize drafts with fields on load
    LaunchedEffect(storedApiKey, storedModel, storedContentType, storedAiProvider, storedFilterFree) {
        apiKeyDraft = storedApiKey
        providerDraft = storedAiProvider
        filterFreeDraft = storedFilterFree
        selectedModelDraft = storedModel
        selectedContentTypeDraft = storedContentType
    }

    // Call dynamic model fetching whenever apiKeyDraft modifies
    LaunchedEffect(apiKeyDraft) {
        if (apiKeyDraft.trim().isNotEmpty() && providerDraft == "openrouter") {
            viewModel.fetchOpenRouterModels(apiKeyDraft)
        }
    }

    val contentTypesList = listOf(
        "Observations", "Roasts", "Affirmations",
        "Surreal Commentary", "Fake Prophecies",
        "Existential Humor", "Emotional Insights", "Contextual Reactions"
    )

    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderBar(
            title = "Settings",
            onThemeToggle = { viewModel.toggleTheme() },
            onBack = onBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: AI Provider Selector
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "AI ENGINE PROVIDER",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                providerDraft = "gemini"
                                selectedModelDraft = "gemini-3.5-flash"
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (providerDraft == "gemini") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(6.dp),
                            border = BoxBorder(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Built-in Gemini",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (providerDraft == "gemini") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        Button(
                            onClick = {
                                providerDraft = "openrouter"
                                if (selectedModelDraft == "gemini-3.5-flash") {
                                    selectedModelDraft = "google/gemini-2.8-flash"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (providerDraft == "openrouter") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(6.dp),
                            border = BoxBorder(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "OpenRouter",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (providerDraft == "openrouter") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            // Section 2: Model Configuration conditional fields
            if (providerDraft == "openrouter") {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "OPENROUTER SETTINGS",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Text(
                            text = "OpenRouter API Key",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        OutlinedTextField(
                            value = apiKeyDraft,
                            onValueChange = { apiKeyDraft = it },
                            placeholder = { Text("sk-or-...") },
                            visualTransformation = PasswordVisualTransformation(),
                            textStyle = MaterialTheme.typography.bodyMedium,
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(); keyboardController?.hide() }),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Select Model",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        when (val state = modelsState) {
                            ModelsLoadingState.Idle -> {
                                Text(
                                    text = "Enter API Key to load models",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                                )
                            }
                            ModelsLoadingState.Loading -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text("Loading models...", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            is ModelsLoadingState.Success -> {
                                val list = if (filterFreeDraft) state.models.filter { it.isFree } else state.models
                                if (list.isEmpty()) {
                                    Text("No models found. Try checking 'Filter Free' toggle.")
                                } else {
                                    // Custom visual Radio layout or simple dropdown. Easy radio selection layout:
                                    var isDropdownOpen by remember { mutableStateOf(false) }
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surface)
                                                .clickable { isDropdownOpen = true }
                                                .padding(12.dp)
                                        ) {
                                            val currentModelLabel = list.find { it.id == selectedModelDraft }?.name ?: selectedModelDraft
                                            Text(currentModelLabel, style = MaterialTheme.typography.bodyMedium)
                                        }

                                        DropdownMenu(
                                            expanded = isDropdownOpen,
                                            onDismissRequest = { isDropdownOpen = false },
                                            modifier = Modifier.fillMaxWidth(0.9f)
                                        ) {
                                            list.forEach { m ->
                                                DropdownMenuItem(
                                                    text = { Text(m.name, style = MaterialTheme.typography.bodyMedium) },
                                                    onClick = {
                                                        selectedModelDraft = m.id
                                                        isDropdownOpen = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            is ModelsLoadingState.Error -> {
                                Text(
                                    text = "Error: ${state.error}",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = RetroRed)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Filter free models checkbox row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = filterFreeDraft,
                                onCheckedChange = { filterFreeDraft = it },
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Show only free models", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else {
                // Built-in Gemini instructions layout card
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "BUILT-IN GEMINI ENGINE",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "The application is currently utilizing the secure AI Studio server-side Gemini 3.5 engine automatically. No custom configuration is necessary.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Section 3: Content Style Selector
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "CONTENT TYPE",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    var isContentDropOpen by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { isContentDropOpen = true }
                                .padding(12.dp)
                        ) {
                            Text(selectedContentTypeDraft, style = MaterialTheme.typography.bodyMedium)
                        }

                        DropdownMenu(
                            expanded = isContentDropOpen,
                            onDismissRequest = { isContentDropOpen = false }
                        ) {
                            contentTypesList.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category, style = MaterialTheme.typography.bodyMedium) },
                                    onClick = {
                                        selectedContentTypeDraft = category
                                        isContentDropOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Section 4: Storage Wipe Reset Button
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DATA MANAGEMENT",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = "All personal answers, historic profiles and credentials are encoded locally on your physical hardware. Cleansing this data immediately wipes the codebase environment state entirely.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 18.sp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Button(
                        onClick = { isResetConfirmOpen = true },
                        colors = ButtonDefaults.buttonColors(containerColor = RetroRed),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "DELETE ALL DATA",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Save Settings Actions Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = BoxBorder(1.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .testTag("settings_back_button")
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text(
                    text = "Back",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Button(
                onClick = {
                    viewModel.saveSettings(
                        apiKey = apiKeyDraft,
                        selectedModel = selectedModelDraft,
                        contentType = selectedContentTypeDraft,
                        aiProvider = providerDraft,
                        filterFreeModels = filterFreeDraft
                    )
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .testTag("settings_save_button")
                    .weight(1f)
                    .height(56.dp)
            ) {
                Text(
                    text = "Save",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }

    // Storage reset confirm dialog
    if (isResetConfirmOpen) {
        AlertDialog(
            onDismissRequest = { isResetConfirmOpen = false },
            title = {
                Text(
                    text = "Full Reset",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you absolutely positive you wish to remove your entire profile, settings credentials and historic results?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isResetConfirmOpen = false
                        viewModel.deleteDataAndReset()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RetroRed)
                ) {
                    Text("Cleanse All", style = MaterialTheme.typography.labelSmall)
                }
            },
            dismissButton = {
                Button(
                    onClick = { isResetConfirmOpen = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BoxBorder(1.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelSmall)
                }
            },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp))
        )
    }
}
