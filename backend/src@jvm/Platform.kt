package com.example

actual fun env(name: String): String? = System.getenv(name)
