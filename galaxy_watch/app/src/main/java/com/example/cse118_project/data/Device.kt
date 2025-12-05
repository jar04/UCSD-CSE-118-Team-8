package com.example.cse118_project.data

data class Device(
    val name: String,
    val address: String,
    var isConnected: Boolean = false
)
