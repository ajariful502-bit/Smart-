package com.example.ui

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppNotification
import com.example.data.Exam
import com.example.data.Result
import com.example.data.ResultRepository
import com.example.data.Student
import com.example.data.Subject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface MarksheetUiState {
    object Idle : MarksheetUiState
    object Loading : MarksheetUiState
    data class Success(
        val student: Student,
        val exam: Exam,
        val subjectGrades: List<SubjectGrade>,
        val overallGpa: Double,
        val overallGrade: String,
        val isPassed: Boolean
    ) : MarksheetUiState
    data class Error(val message: String) : MarksheetUiState
}

data class SubjectGrade(
    val subjectName: String,
    val subjectCode: String,
    val marks: Int,
    val gpa: Double,
    val grade: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ResultRepository
    
    val students: StateFlow<List<Student>>
    val exams: StateFlow<List<Exam>>
    val subjects: StateFlow<List<Subject>>
    val results: StateFlow<List<Result>>
    val notifications: StateFlow<List<AppNotification>>

    // UI Interactive States
    private val _isTeacherLoggedIn = MutableStateFlow(false)
    val isTeacherLoggedIn: StateFlow<Boolean> = _isTeacherLoggedIn.asStateFlow()

    private val _isSeedingActive = MutableStateFlow(false)
    val isSeedingActive: StateFlow<Boolean> = _isSeedingActive.asStateFlow()

    private val _selectedSearchClass = MutableStateFlow("Class 9")
    val selectedSearchClass: StateFlow<String> = _selectedSearchClass.asStateFlow()

    private val _selectedSearchRoll = MutableStateFlow("")
    val selectedSearchRoll: StateFlow<String> = _selectedSearchRoll.asStateFlow()

    private val _selectedSearchExamId = MutableStateFlow<Int?>(null)
    val selectedSearchExamId: StateFlow<Int?> = _selectedSearchExamId.asStateFlow()

    private val _marksheetState = MutableStateFlow<MarksheetUiState>(MarksheetUiState.Idle)
    val marksheetState: StateFlow<MarksheetUiState> = _marksheetState.asStateFlow()

    private val _isDarkMode = MutableStateFlow(true) // Dynamic dark mode by default
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _csvImportStatus = MutableStateFlow<String?>(null)
    val csvImportStatus: StateFlow<String?> = _csvImportStatus.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ResultRepository(database.resultDao())

        students = repository.allStudents.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        exams = repository.allExams.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        subjects = repository.allSubjects.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        results = repository.allResults.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        notifications = repository.allNotifications.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        // Create standard notification channel
        createNotificationChannel()

        // Auto-seed if the database is completely empty on first launch
        viewModelScope.launch {
            try {
                if (repository.allStudents.first().isEmpty()) {
                    seedDemoData()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Toggle Dark Mode
    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun selectSearchClass(className: String) {
        _selectedSearchClass.value = className
        _marksheetState.value = MarksheetUiState.Idle
    }

    fun selectSearchRoll(roll: String) {
        _selectedSearchRoll.value = roll
        _marksheetState.value = MarksheetUiState.Idle
    }

    fun selectSearchExam(examId: Int?) {
        _selectedSearchExamId.value = examId
        _marksheetState.value = MarksheetUiState.Idle
    }

    // Secure Login
    fun postTeacherLogin(pin: String): Boolean {
        return if (pin == "2026" || pin == "1234") {
            _isTeacherLoggedIn.value = true
            sendSystemNotification(
                "শিক্ষক প্রবেশ সেশন",
                "অ্যাডমিন পিন ব্যবহারের মাধ্যমে সফলভাবে শিক্ষক প্যানেলে প্রবেশ করা হয়েছে।"
            )
            viewModelScope.launch {
                repository.insertNotification(
                    AppNotification(
                        title = "শিক্ষক লগইন",
                        message = "অ্যাডমিন পিন (${pin}) ব্যবহার করে শিক্ষক প্যানেলে সফল লগইন।"
                    )
                )
            }
            true
        } else {
            false
        }
    }

    fun logoutTeacher() {
        _isTeacherLoggedIn.value = false
    }

    // Bangladesh Grading Schema
    fun calculateGradeAndGpa(marks: Int): Pair<String, Double> {
        return when {
            marks >= 80 -> Pair("A+", 5.0)
            marks >= 70 -> Pair("A", 4.0)
            marks >= 60 -> Pair("A-", 3.5)
            marks >= 50 -> Pair("B", 3.0)
            marks >= 40 -> Pair("C", 2.0)
            marks >= 33 -> Pair("D", 1.0)
            else -> Pair("F", 0.0)
        }
    }

    // Search Result & Make Report Card
    fun searchStudentResult() {
        val roll = _selectedSearchRoll.value.trim()
        val className = _selectedSearchClass.value
        val examId = _selectedSearchExamId.value

        if (roll.isEmpty()) {
            _marksheetState.value = MarksheetUiState.Error("অনুগ্রহ করে রোল নম্বর প্রদান করুন")
            return
        }
        if (examId == null) {
            _marksheetState.value = MarksheetUiState.Error("অনুগ্রহ করে পরীক্ষা নির্বাচন করুন")
            return
        }

        _marksheetState.value = MarksheetUiState.Loading

        viewModelScope.launch {
            val student = repository.getStudentByRollAndClass(className, roll)
            if (student == null) {
                _marksheetState.value = MarksheetUiState.Error("এই রোল ও ক্লাসের আন্ডারে কোনো ছাত্র পাওয়া যায়নি!")
                return@launch
            }

            val examList = exams.value
            val exam = examList.find { it.id == examId }
            if (exam == null) {
                _marksheetState.value = MarksheetUiState.Error("পরীক্ষা পাওয়া যায়নি!")
                return@launch
            }

            val allResultList = results.value.filter { it.studentId == student.id && it.examId == examId }
            val allSubjectList = subjects.value

            if (allResultList.isEmpty()) {
                _marksheetState.value = MarksheetUiState.Error("এই পরীক্ষার জন্য এখনো কোনো ফলাফল আপলোড করা হয়নি!")
                return@launch
            }

            val subjectGrades = allResultList.mapNotNull { res ->
                val subject = allSubjectList.find { it.id == res.subjectId }
                if (subject != null) {
                    val (grade, gp) = calculateGradeAndGpa(res.marks)
                    SubjectGrade(
                        subjectName = subject.name,
                        subjectCode = subject.code,
                        marks = res.marks,
                        gpa = gp,
                        grade = grade
                    )
                } else null
            }

            // Bangladesh Standard GPA formula:
            // Average of GPA, but if ANY subject gets GPA = 0.0, overall GPA is 0.0 (Failed)
            val hasFailed = subjectGrades.any { it.grade == "F" }
            val overallGpa = if (hasFailed || subjectGrades.isEmpty()) {
                0.0
            } else {
                val sumGpa = subjectGrades.sumOf { it.gpa }
                val avg = sumGpa / subjectGrades.size
                // Round to 2 decimal places
                Math.round(avg * 100).toDouble() / 100
            }

            val overallGrade = when {
                hasFailed -> "F"
                overallGpa >= 5.0 -> "A+"
                overallGpa >= 4.0 -> "A"
                overallGpa >= 3.5 -> "A-"
                overallGpa >= 3.0 -> "B"
                overallGpa >= 2.0 -> "C"
                overallGpa >= 1.0 -> "D"
                else -> "F"
            }

            _marksheetState.value = MarksheetUiState.Success(
                student = student,
                exam = exam,
                subjectGrades = subjectGrades,
                overallGpa = overallGpa,
                overallGrade = overallGrade,
                isPassed = !hasFailed
            )
        }
    }

    // Student CRUD
    fun addStudent(roll: String, name: String, className: String, section: String) {
        viewModelScope.launch {
            val studentExists = repository.getStudentByRollAndClass(className, roll)
            if (studentExists != null) {
                sendSystemNotification("ভুল রেজিস্ট্রেশন", "রোল $roll ইতিমধ্যেই $className এ নিবন্ধিত।")
                return@launch
            }
            val id = repository.insertStudent(Student(roll = roll, name = name, className = className, section = section))
            repository.insertNotification(
                AppNotification(
                    title = "নতুন ছাত্র যোগ করা হয়েছে",
                    message = "রোল: $roll, নাম: $name, ক্লাস: $className সফলভাবে ডাটাবেসে সংরক্ষিত হয়েছে।"
                )
            )
            sendSystemNotification("ছাত্র সংযুক্তি", "নতুন শিক্ষার্থী $name সফলভাবে নিবন্ধিত হয়েছে।")
        }
    }

    fun updateStudent(student: Student) {
        viewModelScope.launch {
            repository.updateStudent(student)
            repository.insertNotification(
                AppNotification(
                    title = "শিক্ষার্থী তথ্য হালনাগাদ",
                    message = "ছাত্র roll: ${student.roll}, name: ${student.name} তথ্য পরিবর্তন করা হয়েছে।"
                )
            )
        }
    }

    fun deleteStudent(studentId: Int) {
        viewModelScope.launch {
            val student = repository.getStudentById(studentId)
            if (student != null) {
                repository.deleteStudentById(studentId)
                repository.insertNotification(
                    AppNotification(
                        title = "ছাত্র মুছে ফেলা হয়েছে",
                        message = "রোল ${student.roll}, নাম: ${student.name} এবং তার সমস্ত ফলাফল ডাটাবেস থেকে মুছে ফেলা হয়েছে।"
                    )
                )
                sendSystemNotification("রেকর্ড অপসারণ", "শিক্ষার্থী ${student.name} এর তথ্য ডাটাবেস থেকে ডিলিট করা হয়েছে।")
            }
        }
    }

    // Result CRUD
    fun saveResult(studentId: Int, examId: Int, subjectId: Int, marks: Int) {
        viewModelScope.launch {
            val result = Result(studentId = studentId, examId = examId, subjectId = subjectId, marks = marks)
            repository.insertResult(result)
            repository.insertNotification(
                AppNotification(
                    title = "ফলাফল ডাটা এন্ট্রি",
                    message = "শিক্ষার্থী আইডি $studentId এর জন্য বিষয় আইডি $subjectId তে $marks মার্ক্স এন্ট্রি সম্পন্ন হয়েছে।"
                )
            )
        }
    }

    fun deleteResult(resultId: Int) {
        viewModelScope.launch {
            repository.deleteResultById(resultId)
        }
    }

    // CSV/Text Bulk Upload Parser
    // Expected Header: Roll,Class,Exam,Subject,Marks
    // Plus optionally, if we want to create student simultaneously:
    // Header: Roll,Name,Class,Section,Exam,Subject,Marks
    fun importCsvData(content: String) {
        viewModelScope.launch {
            _csvImportStatus.value = "ফাইল পার্সিং শুরু হচ্ছে..."
            try {
                val lines = content.lines()
                if (lines.isEmpty()) {
                    _csvImportStatus.value = "ত্রুটি: ফাইলটি সম্পূর্ণ খালি।"
                    return@launch
                }

                val headerLine = lines.firstOrNull()?.replace("\uFEFF", "")?.trim() ?: ""
                val headers = headerLine.split(",").map { it.trim().lowercase() }

                val isFullFormat = headers.contains("name") && headers.contains("section")
                
                var successCount = 0
                var failCount = 0

                val examMap = exams.value.associateBy { it.name.trim().lowercase() }.toMutableMap()
                val subjectMap = subjects.value.associateBy { it.name.trim().lowercase() }.toMutableMap()

                for (i in 1 until lines.size) {
                    val line = lines[i].trim()
                    if (line.isEmpty()) continue

                    val columns = line.split(",").map { it.trim() }
                    try {
                        if (isFullFormat) {
                            // Roll,Name,Class,Section,Exam,Subject,Marks
                            if (columns.size < 7) {
                                failCount++
                                continue
                            }
                            val roll = columns[headers.indexOf("roll")]
                            val name = columns[headers.indexOf("name")]
                            val className = columns[headers.indexOf("class")]
                            val section = columns[headers.indexOf("section")]
                            val examName = columns[headers.indexOf("exam")]
                            val subjectName = columns[headers.indexOf("subject")]
                            val marks = columns[headers.indexOf("marks")].toIntOrNull() ?: 0

                            // 1. Check or insert Student
                            var student = repository.getStudentByRollAndClass(className, roll)
                            if (student == null) {
                                val sId = repository.insertStudent(
                                    Student(roll = roll, name = name, className = className, section = section)
                                ).toInt()
                                student = Student(id = sId, roll = roll, name = name, className = className, section = section)
                            }

                            // 2. Check or insert Exam
                            val examKey = examName.lowercase()
                            var exam = examMap[examKey]
                            if (exam == null) {
                                val eId = repository.insertExam(Exam(name = examName)).toInt()
                                exam = Exam(id = eId, name = examName)
                                examMap[examKey] = exam
                            }

                            // 3. Check or insert Subject
                            val subKey = subjectName.lowercase()
                            var subject = subjectMap[subKey]
                            if (subject == null) {
                                val code = "SUB" + (100 + (subjectMap.size + 1))
                                val sId = repository.insertSubject(Subject(name = subjectName, code = code)).toInt()
                                subject = Subject(id = sId, name = subjectName, code = code)
                                subjectMap[subKey] = subject
                            }

                            // 4. Save result
                            repository.insertResult(
                                Result(studentId = student.id, examId = exam.id, subjectId = subject.id, marks = marks)
                            )
                            successCount++

                        } else {
                            // Roll,Class,Exam,Subject,Marks
                            if (columns.size < 5) {
                                failCount++
                                continue
                            }
                            val roll = columns[headers.indexOf("roll")]
                            val className = columns[headers.indexOf("class")]
                            val examName = columns[headers.indexOf("exam")]
                            val subjectName = columns[headers.indexOf("subject")]
                            val marks = columns[headers.indexOf("marks")].toIntOrNull() ?: 0

                            // Try to look up existing student
                            val student = repository.getStudentByRollAndClass(className, roll)
                            if (student == null) {
                                failCount++ // Missing student
                                continue
                            }

                            // Exam check
                            val examKey = examName.lowercase()
                            var exam = examMap[examKey]
                            if (exam == null) {
                                val eId = repository.insertExam(Exam(name = examName)).toInt()
                                exam = Exam(id = eId, name = examName)
                                examMap[examKey] = exam
                            }

                            // Subject check
                            val subKey = subjectName.lowercase()
                            var subject = subjectMap[subKey]
                            if (subject == null) {
                                val code = "SUB" + (100 + (subjectMap.size + 1))
                                val sId = repository.insertSubject(Subject(name = subjectName, code = code)).toInt()
                                subject = Subject(id = sId, name = subjectName, code = code)
                                subjectMap[subKey] = subject
                            }

                            repository.insertResult(
                                Result(studentId = student.id, examId = exam.id, subjectId = subject.id, marks = marks)
                            )
                            successCount++
                        }
                    } catch (e: Exception) {
                        failCount++
                    }
                }

                _csvImportStatus.value = "সাফল্য: $successCount টি রেকর্ড সফলভাবে ইম্পোর্ট হয়েছে! ব্যর্থতা: $failCount টি রেকর্ড।"
                repository.insertNotification(
                    AppNotification(
                        title = "বাল্ক CSV ডাটা আপলোড",
                        message = "CSV ইম্পোর্ট সফলভাবে সম্পন্ন হয়েছে। মোট সফল রেকর্ড: $successCount, ব্যর্থ রেকর্ড: $failCount"
                    )
                )
                sendSystemNotification(
                    "বাল্ক ডাটা ইম্পোর্ট শেষ",
                    "মোট $successCount টি ফলাফল সফলভাবে ডাটাবেসে সেভ করা হয়েছে।"
                )
            } catch (e: Exception) {
                _csvImportStatus.value = "ত্রুটি: ফাইলটি পার্স করা যায়নি! বিবরণ: ${e.localizedMessage}"
            }
        }
    }

    fun clearCsvImportStatus() {
        _csvImportStatus.value = null
    }

    // Reset notification tray
    fun resetNotifications() {
        viewModelScope.launch {
            repository.clearAllNotifications()
        }
    }

    fun markNotificationAsRead(id: Int) {
        viewModelScope.launch {
            repository.markNotificationAsRead(id)
        }
    }

    // Seeding Demo Data (Questions 4 request: yes)
    fun seedDemoData() {
        if (_isSeedingActive.value) return
        _isSeedingActive.value = true

        viewModelScope.launch {
            // Seed base setup
            val exam1 = repository.insertExam(Exam(name = "১ম সাময়িক পরীক্ষা"))
            val exam2 = repository.insertExam(Exam(name = "অর্ধ-বার্ষিক পরীক্ষা"))
            val exam3 = repository.insertExam(Exam(name = "বার্ষিক পরীক্ষা"))

            val sub1 = repository.insertSubject(Subject(name = "বাংলা", code = "BAN101"))
            val sub2 = repository.insertSubject(Subject(name = "ইংরেজি", code = "ENG102"))
            val sub3 = repository.insertSubject(Subject(name = "গণিত", code = "MAT103"))
            val sub4 = repository.insertSubject(Subject(name = "বিজ্ঞান", code = "SCI104"))
            val sub5 = repository.insertSubject(Subject(name = "ধর্ম ও নৈতিক শিক্ষা", code = "REL105"))

            // Sample Students
            val studentList = listOf(
                Student(roll = "101", name = "আরিফুল ইসলাম", className = "Class 9", section = "A"),
                Student(roll = "102", name = "ফাতেমা আক্তার", className = "Class 9", section = "A"),
                Student(roll = "103", name = "হাসান মাহমুদ", className = "Class 9", section = "A"),
                Student(roll = "104", name = "নুসরাত জাহান", className = "Class 9", section = "B"),
                Student(roll = "105", name = "সাজিদ রহমান", className = "Class 9", section = "B"),
                Student(roll = "201", name = "তাসনিম জাহান", className = "Class 10", section = "A"),
                Student(roll = "202", name = "মো: সিফাত উল্লাহ", className = "Class 10", section = "A"),
                Student(roll = "203", name = "সুমাইয়া ইয়াসমিন", className = "Class 10", section = "A"),
                Student(roll = "204", name = "তানভীর আহমেদ", className = "Class 10", section = "B")
            )

            val sIds = studentList.map { student ->
                repository.insertStudent(student).toInt()
            }

            // Results generator
            // sIds[0] -> Ariful Islam (High marks)
            repository.insertResult(Result(studentId = sIds[0], examId = exam2.toInt(), subjectId = sub1.toInt(), marks = 85))
            repository.insertResult(Result(studentId = sIds[0], examId = exam2.toInt(), subjectId = sub2.toInt(), marks = 78))
            repository.insertResult(Result(studentId = sIds[0], examId = exam2.toInt(), subjectId = sub3.toInt(), marks = 92))
            repository.insertResult(Result(studentId = sIds[0], examId = exam2.toInt(), subjectId = sub4.toInt(), marks = 80))
            repository.insertResult(Result(studentId = sIds[0], examId = exam2.toInt(), subjectId = sub5.toInt(), marks = 88))

            // sIds[1] -> Fatema (Balanced marks)
            repository.insertResult(Result(studentId = sIds[1], examId = exam2.toInt(), subjectId = sub1.toInt(), marks = 72))
            repository.insertResult(Result(studentId = sIds[1], examId = exam2.toInt(), subjectId = sub2.toInt(), marks = 81))
            repository.insertResult(Result(studentId = sIds[1], examId = exam2.toInt(), subjectId = sub3.toInt(), marks = 64))
            repository.insertResult(Result(studentId = sIds[1], examId = exam2.toInt(), subjectId = sub4.toInt(), marks = 76))
            repository.insertResult(Result(studentId = sIds[1], examId = exam2.toInt(), subjectId = sub5.toInt(), marks = 84))

            // sIds[2] -> Hasan Mahmud (One fail to test grading edge cases)
            repository.insertResult(Result(studentId = sIds[2], examId = exam2.toInt(), subjectId = sub1.toInt(), marks = 62))
            repository.insertResult(Result(studentId = sIds[2], examId = exam2.toInt(), subjectId = sub2.toInt(), marks = 50))
            repository.insertResult(Result(studentId = sIds[2], examId = exam2.toInt(), subjectId = sub3.toInt(), marks = 28)) // Failed maths
            repository.insertResult(Result(studentId = sIds[2], examId = exam2.toInt(), subjectId = sub4.toInt(), marks = 67))
            repository.insertResult(Result(studentId = sIds[2], examId = exam2.toInt(), subjectId = sub5.toInt(), marks = 74))

            // sIds[5] -> Tasnim (Class 10 - high marks)
            repository.insertResult(Result(studentId = sIds[5], examId = exam3.toInt(), subjectId = sub1.toInt(), marks = 90))
            repository.insertResult(Result(studentId = sIds[5], examId = exam3.toInt(), subjectId = sub2.toInt(), marks = 86))
            repository.insertResult(Result(studentId = sIds[5], examId = exam3.toInt(), subjectId = sub3.toInt(), marks = 95))
            repository.insertResult(Result(studentId = sIds[5], examId = exam3.toInt(), subjectId = sub4.toInt(), marks = 89))
            repository.insertResult(Result(studentId = sIds[5], examId = exam3.toInt(), subjectId = sub5.toInt(), marks = 93))

            repository.insertNotification(
                AppNotification(
                    title = "ডেমো রেকর্ড সংরক্ষিত",
                    message = "আদর্শ উচ্চ বিদ্যালয়ের নমুনা ছাত্র, পরীক্ষা, বিষয় ও ফলাফল সফলভাবে ডাটাবেসে সিড করা হয়েছে।"
                )
            )

            sendSystemNotification(
                "ডেমো ডাটা সফলভাবে সেট করা হয়েছে",
                "৯ জন নমুনা ছাত্র ও তাদের অর্ধ-বার্ষিক পরীক্ষার ফুল মার্কশিট ডাটাবেসে সংরক্ষিত হয়েছে।"
            )

            _selectedSearchExamId.value = exam2.toInt() // Auto select Half Yearly
            _isSeedingActive.value = false
        }
    }

    // System Notification Manager config
    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "আদর্শ হাই স্কুল নোটিফিকেশন"
                val descriptionText = "ফলাফল ঘোষণা ও অ্যাডমিন সেশনের রিয়েল-টাইম এলার্ট"
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel("adarsha_notifications", name, importance).apply {
                    description = descriptionText
                }
                val notificationManager: NotificationManager =
                    getApplication<Application>().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendSystemNotification(title: String, message: String) {
        try {
            val context = getApplication<Application>()
            val builder = NotificationCompat.Builder(context, "adarsha_notifications")
                .setSmallIcon(android.R.drawable.stat_notify_chat) // Using default android icon safely
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
