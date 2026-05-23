package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "students",
    indices = [Index(value = ["className", "roll"], unique = true)]
)
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val roll: String,
    val name: String,
    val className: String,
    val section: String
)

@Entity(tableName = "exams")
data class Exam(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String
)

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val code: String
)

@Entity(
    tableName = "results",
    indices = [Index(value = ["studentId", "examId", "subjectId"], unique = true)]
)
data class Result(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val studentId: Int,
    val examId: Int,
    val subjectId: Int,
    val marks: Int
)

@Entity(tableName = "notifications")
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
