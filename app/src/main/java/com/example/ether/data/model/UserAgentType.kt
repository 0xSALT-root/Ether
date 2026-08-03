package com.example.ether.data.model

enum class UserAgentType(val title: String, val userAgent: String?) {
    DEFAULT("Default", null),
    IPHONE("iPhone", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"),
    WINDOWS_DESKTOP("Windows Desktop", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"),
    ANDROID_TABLET("Android Tablet", "Mozilla/5.0 (Android 14; Tablet; rv:134.0) Gecko/134.0 Firefox/134.0")
}
