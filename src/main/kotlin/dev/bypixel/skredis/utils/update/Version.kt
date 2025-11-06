package dev.bypixel.skredis.utils.update

data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val tag: String? = null
) : Comparable<Version> {

    companion object {
        fun fromString(versionString: String): Version {
            val cleanVersionString = versionString
                .replace("Optional[", "")
                .replace("]", "")
                .trim()

            val parts = cleanVersionString.split("-", limit = 2)
            val numbers = parts[0].split(".")
            val tag = if (parts.size > 1) parts[1] else null

            val major = numbers.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = numbers.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = numbers.getOrNull(2)?.toIntOrNull() ?: 0

            return Version(major, minor, patch, tag)
        }
    }

    override fun compareTo(other: Version): Int {
        if (major != other.major) return major - other.major
        if (minor != other.minor) return minor - other.minor
        if (patch != other.patch) return patch - other.patch

        return when {
            tag == null && other.tag != null -> 1
            tag != null && other.tag == null -> -1
            tag != null && other.tag != null -> tag.compareTo(other.tag)
            else -> 0
        }
    }
}