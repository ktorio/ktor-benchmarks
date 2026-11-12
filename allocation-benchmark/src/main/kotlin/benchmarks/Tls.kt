package benchmarks

import io.ktor.network.tls.certificates.*
import io.ktor.server.engine.*
import java.io.File
import java.net.InetAddress
import java.security.KeyStore

private const val KEY_ALIAS = "benchmarks"
private const val KEY_PASSWORD = "benchmarks"

/**
 * Self-signed certificate for `localhost` / `127.0.0.1` used by the HTTP/2 (TLS) benchmarks.
 */
private val keyStore: KeyStore by lazy {
    buildKeyStore {
        certificate(KEY_ALIAS) {
            password = KEY_PASSWORD
            domains = listOf("localhost")
            ipAddresses = listOf(InetAddress.getByName("127.0.0.1"))
        }
    }
}

// Tomcat loads the key store from a file
private val keyStoreFile: File by lazy {
    File.createTempFile("benchmarks", ".jks").apply {
        deleteOnExit()
        keyStore.saveToFile(this, KEY_PASSWORD)
    }
}

internal fun ApplicationEngine.Configuration.tlsConnector(port: Int) {
    sslConnector(
        keyStore = keyStore,
        keyAlias = KEY_ALIAS,
        keyStorePassword = { KEY_PASSWORD.toCharArray() },
        privateKeyPassword = { KEY_PASSWORD.toCharArray() },
    ) {
        this.keyStorePath = keyStoreFile
        this.host = "0.0.0.0"
        this.port = port
    }
}
