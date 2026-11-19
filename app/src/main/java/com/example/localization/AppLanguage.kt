package com.example.localization

enum class AppLanguage(
    val code: String,
    val displayName: String,
    val shortBadge: String
) {
    INDONESIAN("id", "Bahasa Indonesia", "ID"),
    ENGLISH("en", "English", "EN"),
    MALAY("ms", "Bahasa Melayu", "MS"),
    CHINESE("zh", "中文 (Mandarin)", "ZH");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: INDONESIAN
    }
}
