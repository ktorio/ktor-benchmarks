import io.ktor.utils.io.core.*
import kotlinx.io.*

fun makeArray(size: Int): ByteArray = buildPacket {
    repeat(size) {
        writeByte(it.toByte())
    }
}.readByteArray()
