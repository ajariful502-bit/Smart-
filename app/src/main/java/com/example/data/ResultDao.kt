package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ResultDao {

    // Students
    @Query("SELECT * FROM students ORDER BY className, name")
    fun getAllStudents(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getStudentById(id: Int): Student?

    @Query("SELECT * FROM students WHERE className = :className AND roll = :roll LIMIT 1")
    suspend fun getStudentByRollAndClass(className: String, roll: String): Student?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: Student): Long

    @Update
    suspend fun updateStudent(student: Student)

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun deleteStudentById(id: Int)

    // Exams
    @Query("SELECT * FROM exams ORDER BY id ASC")
    fun getAllExams(): Flow<List<Exam>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: Exam): Long

    @Query("DELETE FROM exams WHERE id = :id")
    suspend fun deleteExamById(id: Int)

    // Subjects
    @Query("SELECT * FROM subjects ORDER BY id ASC")
    fun getAllSubjects(): Flow<List<Subject>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: Subject): Long

    @Query("DELETE FROM subjects WHERE id = :id")
    suspend fun deleteSubjectById(id: Int)

    // Results
    @Query("SELECT * FROM results")
    fun getAllResults(): Flow<List<Result>>

    @Query("SELECT * FROM results WHERE studentId = :studentId")
    fun getResultsForStudent(studentId: Int): Flow<List<Result>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: Result): Long

    @Update
    suspend fun updateResult(result: Result)

    @Query("DELETE FROM results WHERE id = :id")
    suspend fun deleteResultById(id: Int)

    @Query("DELETE FROM results WHERE studentId = :studentId")
    suspend fun deleteResultsByStudentId(studentId: Int)

    // Notifications
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<AppNotification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: AppNotification): Long

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationAsRead(id: Int)

    @Query("DELETE FROM notifications")
    suspend fun clearAllNotifications()
}
