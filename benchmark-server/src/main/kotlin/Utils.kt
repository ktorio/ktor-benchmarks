import io.ktor.utils.io.core.*

fun makeArray(size: Int): ByteArray = buildPacket {
    repeat(size) {
        writeByte(it.toByte())
    }
}.readBytes()
