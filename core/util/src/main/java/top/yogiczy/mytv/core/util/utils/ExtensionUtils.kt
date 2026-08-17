package top.yogiczy.mytv.core.util.utils

import java.util.regex.Pattern

fun Long.humanizeMs(): String {
    return when (this) {
        in 0..<60_000 -> "${this / 1000}秒"
        in 60_000..<3_600_000 -> "${this / 60_000}分鐘"
        in 3_600_000..<86_400_000 -> "${this / 3_600_000}小時"
        else -> "${this / 86_400_000}天"
    }
}

fun String.isIPv6(): Boolean {
    val urlPattern = Pattern.compile(
        "^((http|https)://)?(\\[[0-9a-fA-F:]+])(:[0-9]+)?(/.*)?$"
    )
    return urlPattern.matcher(this).matches()
}

fun String.compareVersion(version2: String): Int {
    fun parseVersion(version: String): Pair<List<Int>, String?>? {
        val match = Regex(
            pattern = "^v?(\\d+(?:\\.\\d+)*)(?:-([0-9A-Za-z][0-9A-Za-z.-]*))?$",
        ).matchEntire(version.trim()) ?: return null
        val versionNumbers = match.groupValues[1].split(".").map { part ->
            part.toIntOrNull() ?: return null
        }
        return versionNumbers to match.groupValues.getOrNull(2)?.ifBlank { null }
    }

    fun comparePreRelease(label1: String?, label2: String?): Int {
        if (label1 == null && label2 == null) return 0
        if (label1 == null) return 1
        if (label2 == null) return -1

        val parts1 = label1.split(".")
        val parts2 = label2.split(".")
        for (i in 0 until maxOf(parts1.size, parts2.size)) {
            val part1 = parts1.getOrNull(i) ?: return -1
            val part2 = parts2.getOrNull(i) ?: return 1
            if (part1 == part2) continue

            val number1 = part1.toIntOrNull()
            val number2 = part2.toIntOrNull()
            return when {
                number1 != null && number2 != null -> number1.compareTo(number2)
                number1 != null -> -1
                number2 != null -> 1
                else -> part1.compareTo(part2)
            }
        }
        return 0
    }

    val (v1, preRelease1) = parseVersion(this) ?: return 0
    val (v2, preRelease2) = parseVersion(version2) ?: return 0
    val maxLength = maxOf(v1.size, v2.size)

    for (i in 0 until maxLength) {
        val part1 = v1.getOrElse(i) { 0 }
        val part2 = v2.getOrElse(i) { 0 }
        if (part1 > part2) return 1
        if (part1 < part2) return -1
    }

    return comparePreRelease(preRelease1, preRelease2)
}
