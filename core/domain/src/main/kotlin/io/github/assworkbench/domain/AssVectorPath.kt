package io.github.assworkbench.domain

data class AssVectorCommand(
    val command: Char,
    val coordinates: List<Double>,
)

data class AssVectorPath(
    val scale: Int? = null,
    val commands: List<AssVectorCommand>,
)

object AssVectorPathCodec {
    private val commandToken = Regex("""(?i)\b([mnlbspc])\b""")
    private val numberToken = Regex("""[-+]?(?:\d+(?:\.\d*)?|\.\d+)""")

    fun parse(payload: String): AssVectorPath {
        val trimmed = payload.trim()
        val leadingScale = Regex("""^\s*(\d+)\s*,\s*(.*)$""").matchEntire(trimmed)
        val scale = leadingScale?.groupValues?.get(1)?.toIntOrNull()
        val path = leadingScale?.groupValues?.get(2) ?: trimmed
        val markers = commandToken.findAll(path).toList()
        require(markers.isNotEmpty()) { "Vector path has no ASS drawing commands." }

        val commands = markers.mapIndexed { index, marker ->
            val start = marker.range.last + 1
            val end = markers.getOrNull(index + 1)?.range?.first ?: path.length
            val coords = numberToken.findAll(path.substring(start, end)).map { it.value.toDouble() }.toList()
            AssVectorCommand(marker.groupValues[1].lowercase().single(), coords)
        }
        return AssVectorPath(scale, commands)
    }

    fun write(path: AssVectorPath): String = buildString {
        path.scale?.let { append(it).append(',') }
        path.commands.forEachIndexed { index, command ->
            if (index > 0 || path.scale != null) append(' ')
            append(command.command)
            command.coordinates.forEach { value ->
                append(' ').append(format(value))
            }
        }
    }.trim()

    fun movePoint(path: AssVectorPath, commandIndex: Int, coordinatePairIndex: Int, x: Double, y: Double): AssVectorPath {
        require(commandIndex in path.commands.indices)
        val command = path.commands[commandIndex]
        val offset = coordinatePairIndex * 2
        require(offset + 1 < command.coordinates.size)
        val next = command.coordinates.toMutableList()
        next[offset] = x
        next[offset + 1] = y
        return path.copy(commands = path.commands.mapIndexed { i, item ->
            if (i == commandIndex) item.copy(coordinates = next) else item
        })
    }

    private fun format(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString()
        else "%.4f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')
}
