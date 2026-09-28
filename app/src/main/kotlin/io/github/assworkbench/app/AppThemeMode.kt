package io.github.assworkbench.app

enum class AppThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorage(value: String?): AppThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}
