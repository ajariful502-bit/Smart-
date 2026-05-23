package com.example.data

import kotlinx.coroutines.flow.Flow

class ResultRepository(private val resultDao: ResultDao) {

    // Students
    val allStudents: Flow<List<Student>> = resultDao.getAllStudents()

    suspend fun getStudentById(id: Int): Student? = resultDao.getStudentById(id)

    suspend fun getStudentByRollAndClass(className: String, roll: String): Student? {
        return resultDao.getStudentByRollAndClass(className, roll)
    }

    suspend fun insertStudent(student: Student): Long = resultDao.insertStudent(student)

    suspend fun updateStudent(student: Student) = resultDao.updateStudent(student)

    suspend fun deleteStudentById(id: Int) {
        resultDao.deleteResultsByStudentId(id)
        resultDao.deleteStudentById(id)
    }

    // Exams
    val allExams: Flow<List<Exam>> = resultDao.getAllExams()

    suspend fun insertExam(exam: Exam): Long = resultDao.insertExam(exam)

    suspend fun deleteExamById(id: Int) = resultDao.deleteExamById(id)

    // Subjects
    val allSubjects: Flow<List<Subject>> = resultDao.getAllSubjects()

    suspend fun insertSubject(subject: Subject): Long = resultDao.insertSubject(subject)

    suspend fun deleteSubjectById(id: Int) = resultDao.deleteSubjectById(id)

    // Results
    val allResults: Flow<List<Result>> = resultDao.getAllResults()

    fun getResultsForStudent(studentId: Int): Flow<List<Result>> = resultDao.getResultsForStudent(studentId)

    suspend fun insertResult(result: Result): Long = resultDao.insertResult(result)

    suspend fun updateResult(result: Result) = resultDao.updateResult(result)

    suspend fun deleteResultById(id: Int) = resultDao.deleteResultById(id)

    // Notifications
    val allNotifications: Flow<List<AppNotification>> = resultDao.getAllNotifications()

    suspend fun insertNotification(notification: AppNotification): Long = resultDao.insertNotification(notification)

    suspend fun markNotificationAsRead(id: Int) = resultDao.markNotificationAsRead(id)

    suspend fun clearAllNotifications() = resultDao.clearAllNotifications()
}
