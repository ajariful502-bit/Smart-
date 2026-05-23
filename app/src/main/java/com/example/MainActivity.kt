package com.example

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppNotification
import com.example.data.Exam
import com.example.data.Student
import com.example.data.Subject
import com.example.ui.MainViewModel
import com.example.ui.MarksheetUiState
import com.example.ui.SubjectGrade
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.ErrorRed
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val isDarkMode by mainViewModel.isDarkMode.collectAsStateWithLifecycle()

            MyApplicationTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ResultManagerApp(viewModel = mainViewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultManagerApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val isTeacherLoggedIn by viewModel.isTeacherLoggedIn.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(0) } // 0: Students (View results), 1: Teacher Panel
    var showNotificationsTray by remember { mutableStateOf(false) }

    val unreadNotificationsCount = notifications.count { !it.isRead }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "আদর্শ উচ্চ বিদ্যালয়",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        )
                        Text(
                            text = "Adarsha High School | Result Manager",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.toggleDarkMode() },
                        modifier = Modifier.testTag("dark_mode_toggle")
                    ) {
                        Icon(
                            imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Dark Mode",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showNotificationsTray = !showNotificationsTray },
                            modifier = Modifier.testTag("notification_bell_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadNotificationsCount > 0) {
                                        Badge(containerColor = MaterialTheme.colorScheme.tertiary) {
                                            Text(
                                                text = unreadNotificationsCount.toString(),
                                                color = Color.Black,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (unreadNotificationsCount > 0) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications,
                                    contentDescription = "Show Alerts"
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(Icons.Default.School, contentDescription = "Student Workspace") },
                    label = { Text("ছাত্র-ভল্যুম") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.testTag("student_tab_button")
                )
                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(Icons.Default.ManageAccounts, contentDescription = "Teacher Panel") },
                    label = { Text("শিক্ষক পোর্টাল") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.secondary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.testTag("teacher_tab_button")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main content based on active tab
            Crossfade(targetState = activeTab, label = "tab_crossfade") { tab ->
                when (tab) {
                    0 -> StudentResultsView(viewModel = viewModel)
                    1 -> TeacherCabinetView(viewModel = viewModel)
                }
            }

            // Notification Shelf Layer
            AnimatedVisibility(
                visible = showNotificationsTray,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                    )
            ) {
                NotificationTray(
                    notifications = notifications,
                    onDismiss = { showNotificationsTray = false },
                    onClearAll = {
                        viewModel.resetNotifications()
                        Toast.makeText(context, "সব নোটিফিকেশন মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                    },
                    onMarkRead = { id -> viewModel.markNotificationAsRead(id) }
                )
            }
        }
    }
}

// ----------------------------------------------------
// Public View: Search Student Results
// ----------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentResultsView(viewModel: MainViewModel) {
    val context = LocalContext.current
    val isSeedingActive by viewModel.isSeedingActive.collectAsStateWithLifecycle()
    val exams by viewModel.exams.collectAsStateWithLifecycle()
    val selectedClass by viewModel.selectedSearchClass.collectAsStateWithLifecycle()
    val selectedRoll by viewModel.selectedSearchRoll.collectAsStateWithLifecycle()
    val selectedExamId by viewModel.selectedSearchExamId.collectAsStateWithLifecycle()
    val marksheetState by viewModel.marksheetState.collectAsStateWithLifecycle()

    var isExamDropdownExpanded by remember { mutableStateOf(false) }

    // PDF download SAF launcher
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    (marksheetState as? MarksheetUiState.Success)?.let { successState ->
                        generateAndSavePdf(context, successState, outputStream)
                        Toast.makeText(context, "মার্কশিট PDF সফলভাবে ডাউনলোড হয়েছে!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "ডাউনলোড ব্যর্থ হযেছে: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Academy Banner Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = "Badge",
                        modifier = Modifier.size(54.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "আদর্শ উচ্চ বিদ্যালয়, ঢাকা",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "ডিজিটাল মার্কশিট বিতরণ পদ্ধতি - ২০২৬",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }

        item {
            // Search criteria box
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "শিক্ষার্থীর বিবরণ দিন",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Class Picker (Scrollable Row)
                    Text("শ্রেণী নির্বাচন করুন:", style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Class 9", "Class 10").forEach { cls ->
                            val isSelected = selectedClass == cls
                            Button(
                                onClick = { viewModel.selectSearchClass(cls) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("class_tab_$cls")
                            ) {
                                Text(if (cls == "Class 9") "৯ম শ্রেণী" else "১০ম শ্রেণী")
                            }
                        }
                    }

                    // Exam Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExposedDropdownMenuBox(
                            expanded = isExamDropdownExpanded,
                            onExpandedChange = { isExamDropdownExpanded = it }
                        ) {
                            val selectedExam = exams.find { it.id == selectedExamId }
                            OutlinedTextField(
                                value = selectedExam?.name ?: "পরীক্ষা নির্বাচন করুন",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("পরীক্ষার নাম") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExamDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .testTag("exam_dropdown_input"),
                                colors = OutlinedTextFieldDefaults.colors()
                            )
                            ExposedDropdownMenu(
                                expanded = isExamDropdownExpanded,
                                onDismissRequest = { isExamDropdownExpanded = false }
                            ) {
                                if (exams.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("কোনো পরীক্ষা সভার খোঁজ পাওয়া যায়নি") },
                                        onClick = { isExamDropdownExpanded = false }
                                    )
                                } else {
                                    exams.forEach { exam ->
                                        DropdownMenuItem(
                                            text = { Text(exam.name) },
                                            onClick = {
                                                viewModel.selectSearchExam(exam.id)
                                                isExamDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Roll input
                    OutlinedTextField(
                        value = selectedRoll,
                        onValueChange = { viewModel.selectSearchRoll(it) },
                        label = { Text("রোল নম্বর লিখুন (উদা: 101, 201)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = "Roll") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("roll_search_input")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = { viewModel.searchStudentResult() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("search_result_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ফলাফল অনুসন্ধান করুন", fontWeight = FontWeight.Bold)
                    }

                    if (exams.isEmpty() && !isSeedingActive) {
                        Button(
                            onClick = { viewModel.seedDemoData() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("seed_demo_data_button")
                        ) {
                            Text("নমুনা ডেমো ডাটা লোড করুন (Seed)", color = Color.Black)
                        }
                    }
                }
            }
        }

        // Search State / Marksheet view
        item {
            AnimatedContent(targetState = marksheetState, label = "marksheet_transition") { state ->
                when (state) {
                    is MarksheetUiState.Idle -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FindInPage,
                                contentDescription = "Search Student",
                                size = 48.dp,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "শ্রেণী, পরীক্ষা ও শিক্ষার্থীর রোল টাইপ করে সার্চ করুন।",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    is MarksheetUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    }
                    is MarksheetUiState.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    is MarksheetUiState.Success -> {
                        ResultCard(
                            successState = state,
                            onDownloadPdf = {
                                createPdfLauncher.launch("Adarsha_Marksheet_${state.student.className}_Roll_${state.student.roll}.pdf")
                            },
                            onShare = {
                                shareMarksheet(context, state)
                            }
                        )
                    }
                }
            }
        }

        item {
            DeveloperCreditsCard(modifier = Modifier.padding(top = 16.dp))
        }
    }
}

// ----------------------------------------------------
// UI Card Component: Report Card/Result sheet
// ----------------------------------------------------
@Composable
fun ResultCard(
    successState: MarksheetUiState.Success,
    onDownloadPdf: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("student_marksheet_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Certificate Design
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = "একাডেমিক ট্রান্সক্রিপ্ট",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "ACADEMIC TRANSCRIPT",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
                Divider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
                Text(
                    text = successState.exam.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            // Student Metrics Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "নাম: ${successState.student.name}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "ক্লাস: ${if (successState.student.className == "Class 9") "৯ম" else "১০ম"} শ্রেণী",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "সেকশন: ${successState.student.section}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "রোল নম্বর: ${successState.student.roll}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val statusBg = if (successState.isPassed) SuccessGreen.copy(alpha = 0.15f) else ErrorRed.copy(alpha = 0.15f)
                    val statusText = if (successState.isPassed) "উত্তীর্ণ (Passed)" else "অনুত্তীর্ণ (Failed)"
                    val statusColor = if (successState.isPassed) SuccessGreen else ErrorRed
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(statusBg)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Results Detail Tree/Table
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Table Headers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("বিষয়", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.8f))
                    Text("মার্ক্স", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text("জিপিএ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    Text("গ্রেড", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                // Subject Scores Row
                successState.subjectGrades.forEach { grade ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = grade.subjectName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1.8f)
                        )
                        Text(
                            text = grade.marks.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = String.format("%.2f", grade.gpa),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = grade.grade,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.End,
                            fontWeight = FontWeight.Bold,
                            color = if (grade.grade == "F") ErrorRed else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Overall Summary Result
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer
                            )
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "গড় জিপিএ (GPA):",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 12.sp
                    )
                    Text(
                        text = String.format("%.2f", successState.overallGpa),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 32.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "প্রাপ্ত গ্রেড (Grade):",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 12.sp
                    )
                    Text(
                        text = successState.overallGrade,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 32.sp,
                        color = if (successState.overallGrade == "F") ErrorRed else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Downloader Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_marksheet_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("শেয়ার করুন")
                }
                Button(
                    onClick = onDownloadPdf,
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("download_pdf_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PDF ডাউনলোড")
                }
            }
        }
    }
}

// ----------------------------------------------------
// Share Marksheet Helper Method
// ----------------------------------------------------
fun shareMarksheet(context: Context, state: MarksheetUiState.Success) {
    val resultsText = state.subjectGrades.joinToString("\n") {
        "${it.subjectName}: ${it.marks} (GP: ${String.format("%.2f", it.gpa)}, Grade: ${it.grade})"
    }
    val shareBody = """
        🏫 আদর্শ উচ্চ বিদ্যালয়, ঢাকা
        📝 একাডেমিক পরীক্ষার ফলাফল
        -----------------------------------
        শিক্ষার্থীর নাম: ${state.student.name}
        শ্রেণী: ${if (state.student.className == "Class 9") "৯ম" else "১০ম"} | রোল: ${state.student.roll}
        পরীক্ষা: ${state.exam.name}
        -----------------------------------
        $resultsText
        -----------------------------------
        গড় জিপিএ (GPA): ${String.format("%.2f", state.overallGpa)} (প্রাপ্ত গ্রেড: ${state.overallGrade})
        ফলাফল স্ট্যাটাস: ${if (state.isPassed) "উত্তীর্ণ (Passed)" else "অনুত্তীর্ণ (Failed)"}
        
        Adarsha High School Result Manager App
    """.trimIndent()

    val sendIntent = android.content.Intent().apply {
        action = android.content.Intent.ACTION_SEND
        putExtra(android.content.Intent.EXTRA_TEXT, shareBody)
        type = "text/plain"
    }
    val shareIntent = android.content.Intent.createChooser(sendIntent, "ফলাফল শেয়ার করুন")
    context.startActivity(shareIntent)
}

// ----------------------------------------------------
// Native offline PDF Document Generator Block
// ----------------------------------------------------
fun generateAndSavePdf(
    context: Context,
    successState: MarksheetUiState.Success,
    outputStream: OutputStream
) {
    val pdfDocument = PdfDocument()
    // A4 Dimension units: 595 x 842 pt
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas

    val paint = Paint().apply {
        isAntiAlias = true
    }

    // 1. Draw Page border
    paint.color = android.graphics.Color.DKGRAY
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f
    canvas.drawRect(20f, 20f, 575f, 822f, paint)

    paint.color = android.graphics.Color.LTGRAY
    paint.strokeWidth = 0.5f
    canvas.drawRect(24f, 24f, 571f, 818f, paint)

    paint.style = Paint.Style.FILL

    // 2. School Title banner block
    paint.color = android.graphics.Color.argb(255, 2, 132, 199) // Sky Blue Academic Color
    canvas.drawRect(30f, 30f, 565f, 130f, paint)

    paint.color = android.graphics.Color.WHITE
    paint.textSize = 24f
    paint.isFakeBoldText = true
    canvas.drawText("ADARSHA HIGH SCHOOL, DHAKA", 50f, 75f, paint)

    paint.textSize = 10f
    paint.isFakeBoldText = false
    canvas.drawText("ESTD: 1985 | EXCELLENCE IN EDUCATION", 50f, 95f, paint)
    canvas.drawText("ONLINE STUDENT ACADEMIC MARKSHEET SYSTEM", 50f, 115f, paint)

    // 3. Marksheet Title
    paint.color = android.graphics.Color.BLACK
    paint.textSize = 16f
    paint.isFakeBoldText = true
    canvas.drawText("ACADEMIC PROGRESS REPORT CARD", 160f, 170f, paint)

    paint.color = android.graphics.Color.DKGRAY
    paint.textSize = 11f
    paint.isFakeBoldText = false
    canvas.drawText("Exam Instance: ${successState.exam.name}", 50f, 200f, paint)
    canvas.drawText("Date: ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date())}", 370f, 200f, paint)

    paint.color = android.graphics.Color.LTGRAY
    canvas.drawLine(40f, 210f, 555f, 210f, paint)

    // 4. Student parameters table
    paint.color = android.graphics.Color.BLACK
    paint.textSize = 11f
    canvas.drawText("Student Name  : ${successState.student.name}", 50f, 235f, paint)
    canvas.drawText("Grade/Class   : ${successState.student.className}", 50f, 255f, paint)
    canvas.drawText("Section       : ${successState.student.section}", 50f, 275f, paint)

    canvas.drawText("Roll Number   : ${successState.student.roll}", 330f, 235f, paint)
    canvas.drawText("Academic Year : 2026", 330f, 255f, paint)
    val statusTxt = if (successState.isPassed) "Status: PASSED / PROMOTED" else "Status: FAILED / NOT PROMOTED"
    canvas.drawText(statusTxt, 330f, 275f, paint)

    paint.color = android.graphics.Color.LTGRAY
    canvas.drawLine(40f, 290f, 555f, 290f, paint)

    // 5. Subject wise Table header
    paint.color = android.graphics.Color.argb(255, 13, 148, 136) // Emerald green table header
    canvas.drawRect(40f, 305f, 555f, 330f, paint)

    paint.color = android.graphics.Color.WHITE
    paint.isFakeBoldText = true
    canvas.drawText("Subject Course", 50f, 321f, paint)
    canvas.drawText("Code", 240f, 321f, paint)
    canvas.drawText("Marks", 330f, 321f, paint)
    canvas.drawText("GPA Pt", 410f, 321f, paint)
    canvas.drawText("Letter Grade", 480f, 321f, paint)

    paint.color = android.graphics.Color.BLACK
    paint.isFakeBoldText = false

    // 6. Draw Rows
    var yOffset = 355f
    successState.subjectGrades.forEach { sg ->
        canvas.drawText(sg.subjectName, 50f, yOffset, paint)
        canvas.drawText(sg.subjectCode, 240f, yOffset, paint)
        canvas.drawText(sg.marks.toString(), 330f, yOffset, paint)
        canvas.drawText(String.format("%.2f", sg.gpa), 410f, yOffset, paint)
        paint.isFakeBoldText = true
        canvas.drawText(sg.grade, 480f, yOffset, paint)
        paint.isFakeBoldText = false

        paint.color = android.graphics.Color.argb(255, 241, 245, 249)
        canvas.drawLine(40f, yOffset + 10f, 555f, yOffset + 10f, paint)
        paint.color = android.graphics.Color.BLACK

        yOffset += 30f
    }

    // 7. Overall GPA summary block
    yOffset += 20f
    paint.color = android.graphics.Color.argb(255, 248, 250, 252)
    canvas.drawRect(40f, yOffset, 555f, yOffset + 80f, paint)

    paint.color = android.graphics.Color.DKGRAY
    paint.textSize = 12f
    paint.isFakeBoldText = true
    canvas.drawText("OVERALL VALEDICTORY GPA", 60f, yOffset + 30f, paint)

    paint.color = android.graphics.Color.BLACK
    paint.textSize = 28f
    canvas.drawText(String.format("%.2f", successState.overallGpa), 60f, yOffset + 65f, paint)

    paint.color = android.graphics.Color.DKGRAY
    paint.textSize = 12f
    canvas.drawText("FINAL LETTER GRADE", 350f, yOffset + 30f, paint)

    paint.color = if (successState.overallGrade == "F") android.graphics.Color.RED else android.graphics.Color.BLACK
    paint.textSize = 28f
    canvas.drawText(successState.overallGrade, 350f, yOffset + 65f, paint)

    // 8. Signatures row
    paint.color = android.graphics.Color.BLACK
    paint.textSize = 10f
    paint.isFakeBoldText = false
    canvas.drawLine(60f, 750f, 180f, 750f, paint)
    canvas.drawText("Prepared By", 85f, 765f, paint)

    canvas.drawLine(380f, 750f, 500f, 750f, paint)
    canvas.drawText("Principal Seal", 405f, 765f, paint)

    pdfDocument.finishPage(page)
    pdfDocument.writeTo(outputStream)
    pdfDocument.close()
}

// ----------------------------------------------------
// Secure Panel: Teacher Desk Layout
// ----------------------------------------------------
@Composable
fun TeacherCabinetView(viewModel: MainViewModel) {
    val isTeacherLoggedIn by viewModel.isTeacherLoggedIn.collectAsStateWithLifecycle()

    AnimatedContent(targetState = isTeacherLoggedIn, label = "cabinet_transition") { loggedIn ->
        if (loggedIn) {
            TeacherDashboard(viewModel = viewModel)
        } else {
            TeacherPinLogin(onLogin = { pin -> viewModel.postTeacherLogin(pin) })
        }
    }
}

// ----------------------------------------------------
// UI Sub-Screen: Keypad-styled PIN Lock layout
// ----------------------------------------------------
@Composable
fun TeacherPinLogin(onLogin: (String) -> Boolean) {
    var passwordInput by remember { mutableStateOf("") }
    var loginFailedAlert by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Crest
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                Text(
                    text = "শিক্ষক প্রমাণীকরণ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                Text(
                    text = "প্যানেলে প্রবেশ করতে ৪ ডিজিটের অ্যাডমিন পিন কোড দিন",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Dots display
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    for (i in 1..4) {
                        val isFilled = passwordInput.length >= i
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isFilled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    CircleShape
                                )
                        )
                    }
                }

                if (loginFailedAlert) {
                    Text(
                        text = "পিন ভুল হয়েছে! অনুগ্রহ করে আবার চেষ্টা করুন।",
                        color = ErrorRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Password Keyboard Panel (1 - 9 Grid)
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "OK")
                    )
                    keys.forEach { rowKeys ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowKeys.forEach { key ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (key == "OK") MaterialTheme.colorScheme.secondaryContainer
                                            else if (key == "C") MaterialTheme.colorScheme.errorContainer.copy(
                                                alpha = 0.5f
                                            )
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable {
                                            loginFailedAlert = false
                                            when (key) {
                                                "C" -> {
                                                    if (passwordInput.isNotEmpty()) {
                                                        passwordInput = passwordInput.dropLast(1)
                                                    }
                                                }
                                                "OK" -> {
                                                    if (passwordInput.length == 4) {
                                                        val authenticated = onLogin(passwordInput)
                                                        if (!authenticated) {
                                                            loginFailedAlert = true
                                                            passwordInput = ""
                                                        }
                                                    }
                                                }
                                                else -> {
                                                    if (passwordInput.length < 4) {
                                                        passwordInput += key
                                                    }
                                                }
                                            }
                                        }
                                        .testTag("keypad_$key"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = if (key == "OK") MaterialTheme.colorScheme.secondary else if (key == "C") ErrorRed else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = "*ডেমো পিন কোড: 2026 বা 1234",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        DeveloperCreditsCard()
    }
}

// ----------------------------------------------------
// Authenticated Teacher Operations Menu Dashboard
// ----------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherDashboard(viewModel: MainViewModel) {
    val context = LocalContext.current
    val students by viewModel.students.collectAsStateWithLifecycle()
    val exams by viewModel.exams.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    var activeSubMenu by remember { mutableStateOf(0) } // 0: Student enrollment, 1: Create results, 2: Bulk CSV

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Teacher Dashboard Control Deck Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "শিক্ষক কন্ট্রোল প্যানেল",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Adarsha Teacher Dashboard",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { viewModel.logoutTeacher() },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                    .testTag("teacher_logout_button")
            ) {
                Icon(
                    Icons.Default.ExitToApp,
                    contentDescription = "Log Out",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        // Stats Overlay bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val stats = listOf(
                Pair(students.size.toString(), "মোট ছাত্র"),
                Pair(exams.size.toString(), "মোট পরীক্ষা"),
                Pair(results.size.toString(), "রেজাল্ট এন্ট্রি")
            )
            stats.forEach { stat ->
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stat.first,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = stat.second,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Sub control navigation tabs
        ScrollableTabRow(
            selectedTabIndex = activeSubMenu,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = activeSubMenu == 0,
                onClick = { activeSubMenu = 0 },
                text = { Text("ছাত্র তালিকা") },
                modifier = Modifier.testTag("teacher_students_tab")
            )
            Tab(
                selected = activeSubMenu == 1,
                onClick = { activeSubMenu = 1 },
                text = { Text("ফলাফল দিন") },
                modifier = Modifier.testTag("teacher_results_tab")
            )
            Tab(
                selected = activeSubMenu == 2,
                onClick = { activeSubMenu = 2 },
                text = { Text("বাল্ক ইম্পোর্ট") },
                modifier = Modifier.testTag("teacher_bulk_tab")
            )
        }

        // Expanded Control views
        Box(modifier = Modifier.weight(1f)) {
            when (activeSubMenu) {
                0 -> StudentEnrollPanel(viewModel = viewModel, students = students)
                1 -> ResultEntryPanel(
                    viewModel = viewModel,
                    students = students,
                    exams = exams,
                    subjects = subjects
                )
                2 -> BulkCsvImportPanel(viewModel = viewModel)
            }
        }
    }
}

// ----------------------------------------------------
// Panel: Add Student / List Registered students
// ----------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentEnrollPanel(viewModel: MainViewModel, students: List<Student>) {
    var roll by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var selectedClass by remember { mutableStateOf("Class 9") }
    var section by remember { mutableStateOf("A") }

    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Enrolling expand card
        var isAddNewSheetOpen by remember { mutableStateOf(false) }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isAddNewSheetOpen = !isAddNewSheetOpen }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = "Add",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("নতুন শিক্ষার্থী যোগ করুন", fontWeight = FontWeight.Bold)
                    }
                    Icon(
                        imageVector = if (isAddNewSheetOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand info"
                    )
                }

                AnimatedVisibility(visible = isAddNewSheetOpen) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = roll,
                            onValueChange = { roll = it },
                            label = { Text("রোল নম্বর লিখুন (উদা: 101)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("enroll_student_roll")
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("পূর্ণ নাম (উদা: আরিফুল ইসলাম)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("enroll_student_name")
                        )

                        // Class list select
                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf("Class 9", "Class 10").forEach { cls ->
                                val isSel = selectedClass == cls
                                Button(
                                    onClick = { selectedClass = cls },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Text(if (cls == "Class 9") "৯ম শ্রেণী" else "১০ম শ্রেণী")
                                }
                            }
                        }

                        // Section list select
                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf("A", "B").forEach { sec ->
                                val isSel = section == sec
                                Button(
                                    onClick = { section = sec },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Text("সেকশন $sec")
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (roll.trim().isEmpty() || name.trim().isEmpty()) {
                                    Toast.makeText(context, "সব তথ্য পূরণ করুন", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.addStudent(roll.trim(), name.trim(), selectedClass, section)
                                Toast.makeText(context, "নতুন শিক্ষার্থী সফলভাবে নিবন্ধিত হয়েছে!", Toast.LENGTH_SHORT).show()
                                roll = ""
                                name = ""
                                isAddNewSheetOpen = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("enroll_save_button")
                        ) {
                            Text("তথ্য ডাটাবেসে সেভ করুন")
                        }
                    }
                }
            }
        }

        // Student List Header
        Text(
            text = "নিবন্ধিত শিক্ষার্থীবৃন্দ তালিকা (${students.size})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        // List Scroll
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(students) { student ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${student.name} (রোল: ${student.roll})",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "ক্লাস: ${if (student.className == "Class 9") "৯ম" else "১০ম"} | সেকশন: ${student.section}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { viewModel.deleteStudent(student.id) },
                            modifier = Modifier.testTag("delete_student_${student.roll}")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete student",
                                tint = ErrorRed
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// Panel: Input Single Exam results
// ----------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultEntryPanel(
    viewModel: MainViewModel,
    students: List<Student>,
    exams: List<Exam>,
    subjects: List<Subject>
) {
    val context = LocalContext.current

    var selectedStudentIndex by remember { mutableStateOf(0) }
    var selectedExamIndex by remember { mutableStateOf(0) }
    var selectedSubjectIndex by remember { mutableStateOf(0) }
    var marksInput by remember { mutableStateOf("") }

    var studentExpanded by remember { mutableStateOf(false) }
    var examExpanded by remember { mutableStateOf(false) }
    var subjectExpanded by remember { mutableStateOf(false) }

    if (students.isEmpty() || exams.isEmpty() || subjects.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Dangerous, contentDescription = "No data", modifier = Modifier.size(48.dp), tint = ErrorRed)
                Spacer(modifier = Modifier.height(10.dp))
                Text("তথ্য পাওয়া যায়নি!", fontWeight = FontWeight.Bold)
                Text(
                    text = "ফলাফল বসানোর আগে অবশ্যই শিক্ষার্থী, পরীক্ষা ও বিষয় তৈরি বা সিড করতে হবে।",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("একক পরীক্ষা মার্কিং ড্রপডাউন", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    // 1. Selector Student
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExposedDropdownMenuBox(
                            expanded = studentExpanded,
                            onExpandedChange = { studentExpanded = it }
                        ) {
                            val activeStudent = students.getOrNull(selectedStudentIndex)
                            OutlinedTextField(
                                value = activeStudent?.let { "${it.name} (${it.roll} - ${it.className})" } ?: "নির্বাচন করুন",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("১. শিক্ষার্থী নির্বাচন") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = studentExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = studentExpanded,
                                onDismissRequest = { studentExpanded = false }
                            ) {
                                students.forEachIndexed { idx, st ->
                                    DropdownMenuItem(
                                        text = { Text("${st.name} (রোল: ${st.roll}, ${st.className})") },
                                        onClick = {
                                            selectedStudentIndex = idx
                                            studentExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 2. Selector Exam
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExposedDropdownMenuBox(
                            expanded = examExpanded,
                            onExpandedChange = { examExpanded = it }
                        ) {
                            val activeExam = exams.getOrNull(selectedExamIndex)
                            OutlinedTextField(
                                value = activeExam?.name ?: "নির্বাচন করুন",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("২. পরীক্ষা নির্বাচন") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = examExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = examExpanded,
                                onDismissRequest = { examExpanded = false }
                            ) {
                                exams.forEachIndexed { idx, ex ->
                                    DropdownMenuItem(
                                        text = { Text(ex.name) },
                                        onClick = {
                                            selectedExamIndex = idx
                                            examExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 3. Selector Subject
                    Box(modifier = Modifier.fillMaxWidth()) {
                        ExposedDropdownMenuBox(
                            expanded = subjectExpanded,
                            onExpandedChange = { subjectExpanded = it }
                        ) {
                            val activeSubject = subjects.getOrNull(selectedSubjectIndex)
                            OutlinedTextField(
                                value = activeSubject?.let { "${it.name} (${it.code})" } ?: "নির্বাচন করুন",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("৩. বিষয় কোর্স নির্বাচন") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = subjectExpanded,
                                onDismissRequest = { subjectExpanded = false }
                            ) {
                                subjects.forEachIndexed { idx, sj ->
                                    DropdownMenuItem(
                                        text = { Text("${sj.name} (${sj.code})") },
                                        onClick = {
                                            selectedSubjectIndex = idx
                                            subjectExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 4. Marks Input Value (0-100)
                    OutlinedTextField(
                        value = marksInput,
                        onValueChange = { marksInput = it },
                        label = { Text("প্রাপ্ত গ্রেড মার্ক্স (0-100)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("result_marks_input")
                    )

                    Button(
                        onClick = {
                            val student = students.getOrNull(selectedStudentIndex) ?: return@Button
                            val exam = exams.getOrNull(selectedExamIndex) ?: return@Button
                            val subject = subjects.getOrNull(selectedSubjectIndex) ?: return@Button
                            val marks = marksInput.trim().toIntOrNull()

                            if (marks == null || marks !in 0..100) {
                                Toast.makeText(context, "অনুগ্রহ করে ০ থেকে ১০০ এর মধ্যে সঠিক মার্ক বসান।", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            viewModel.saveResult(student.id, exam.id, subject.id, marks)
                            Toast.makeText(context, "প্রাপ্ত নম্বর সফলভাবে সাবমিট হয়েছে!", Toast.LENGTH_SHORT).show()
                            marksInput = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("result_submit_button")
                    ) {
                        Text("ফলাফল রেকর্ড সেভ করুন (Save)")
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// Panel: CSV Bulk upload & Text area editor tool
// ----------------------------------------------------
@Composable
fun BulkCsvImportPanel(viewModel: MainViewModel) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val csvImportStatus by viewModel.csvImportStatus.collectAsStateWithLifecycle()

    var manualCsvText by remember { mutableStateOf("") }

    // CSV Picker launcher
    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val content = inputStream.bufferedReader().use { r -> r.readText() }
                    viewModel.importCsvData(content)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "ফাইল লোড ব্যর্থ: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "CSV ডাটা আপলোড নির্দেশিকা",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "ডিভাইসের CSV ফাইল সিলেক্ট করুন বা নিচে সরাসরি টেক্সট পেস্ট করে আপলোড করতে পারেন।",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Template specification details
                    Text(
                        text = "ফরম্যাট ১ Header (যদি শিক্ষার্থী ডাটাবেসে পূর্ব থেকেই থাকে):\n" +
                                "Roll,Class,Exam,Subject,Marks\n" +
                                "উদাহরণ:\n" +
                                "101,Class 9,অর্ধ-বার্ষিক পরীক্ষা,গণিত,85",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                    )

                    Text(
                        text = "ফরম্যাট ২ Header (নতুন শিক্ষার্থীসহ ফুল এন্ট্রি):\n" +
                                "Roll,Name,Class,Section,Exam,Subject,Marks\n" +
                                "উদাহরণ:\n" +
                                "110,করিমুল ইসলাম,Class 9,A,বার্ষিক পরীক্ষা,ইংরেজি,72",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                    )

                    // Copy Template button
                    Button(
                        onClick = {
                            val sample = """
                                Roll,Name,Class,Section,Exam,Subject,Marks
                                111,আল আমিন,Class 9,A,বার্ষিক পরীক্ষা,গণিত,88
                                112,তানজিলা খাতুন,Class 9,A,বার্ষিক পরীক্ষা,গণিত,78
                                113,ফারহান হাসান,Class 10,B,বার্ষিক পরীক্ষা,ইংরেজি,92
                            """.trimIndent()
                            clipboardManager.setText(AnnotatedString(sample))
                            Toast.makeText(context, "নমুনা টেমপ্লেট ক্লিপবোর্ডে কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("copy_csv_template_button")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy template")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("নমুনা ইম্পোর্ট কপি করুন")
                    }
                }
            }
        }

        // CSV select SAF button trigger
        item {
            Button(
                onClick = { importCsvLauncher.launch("text/*") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bulk_csv_picker_button")
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = "CSV document selection")
                Spacer(modifier = Modifier.width(8.dp))
                Text("CSV ফাইল আপলোড করুন")
            }
        }

        // Direct Text pasting input block
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("অথবা সরাসরি লিখুন/পেস্ট করুন (CSV Data Input)", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = manualCsvText,
                        onValueChange = { manualCsvText = it },
                        placeholder = { Text("Roll,Name,Class,Section,Exam,Subject,Marks\n114,সাইফুল ইসলাম,Class 9,A,বার্ষিক পরীক্ষা,গণিত,85") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .testTag("manual_csv_text_input"),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )
                    Button(
                        onClick = {
                            if (manualCsvText.trim().isEmpty()) {
                                Toast.makeText(context, "অনুগ্রহ করে টেক্সট লিখুন", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.importCsvData(manualCsvText)
                            manualCsvText = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_csv_submit_button")
                    ) {
                        Text("ডাটা ইম্পোর্ট প্রসেস করুন")
                    }
                }
            }
        }

        // Status report dialogue
        item {
            if (csvImportStatus != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (csvImportStatus!!.contains("ত্রুটি")) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ইম্পোর্ট স্ট্যাটাস রিপোর্ট:",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = { viewModel.clearCsvImportStatus() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close status")
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = csvImportStatus!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (csvImportStatus!!.contains("ত্রুটি")) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// Alert / Log / Activity Tray component overlay
// ----------------------------------------------------
@Composable
fun NotificationTray(
    notifications: List<AppNotification>,
    onDismiss: () -> Unit,
    onClearAll: () -> Unit,
    onMarkRead: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.NotificationsActive,
                    contentDescription = "Alerts",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("রিয়েল-টাইম নোটিফিকেশন ট্রে", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Collapse Shelf")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onClearAll,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .weight(1f)
                    .testTag("clear_notifications_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("সব নোটিফিকেশন মুছুন")
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("বন্ধ করুন")
            }
        }

        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

        if (notifications.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.Notifications,
                        contentDescription = "No alerts",
                        size = 32.dp,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "কোন নোটিফিকেশন পাওয়া যায়নি",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notifications) { notif ->
                    val colorAlpha = if (notif.isRead) 0.05f else 0.12f
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMarkRead(notif.id) }
                            .testTag("notif_item_${notif.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (notif.isRead) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.primary.copy(alpha = colorAlpha)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = notif.title,
                                    fontWeight = if (notif.isRead) FontWeight.SemiBold else FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (notif.isRead) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary
                                )
                                val timeText = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(notif.timestamp))
                                Text(
                                    text = timeText,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = notif.message,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// Safe drawing Extension specs for Custom Icon sizes
// ----------------------------------------------------
@Composable
fun Icon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    size: androidx.compose.ui.unit.Dp,
    tint: androidx.compose.ui.graphics.Color
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier.size(size)
    )
}

// ----------------------------------------------------
// Developer Credits Card Section
// ----------------------------------------------------
@Composable
fun DeveloperCreditsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("developer_info_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .clickable {
                    try {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            Uri.parse("https://www.facebook.com/mdarifulislam15")
                        )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "লিংক ওপেন করা সম্ভব হয়নি", Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = "Code Icon",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ডেভলপার তথ্য",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "মোঃ আরিফুল ইসলাম",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "যোগাযোগ করতে ট্যাপ করুন",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Facebook link",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "ফেসবুক প্রোফাইল",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
