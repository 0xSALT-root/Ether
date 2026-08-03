package com.example.ether.ui.util

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object UrlUtils {
    fun encode(url: String): String = URLEncoder.encode(url, StandardCharsets.UTF_8.name())
    fun decode(url: String): String = URLDecoder.decode(url, StandardCharsets.UTF_8.name())
}
