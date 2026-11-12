package benchmarks.utils

import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Trusts the self-signed certificates used by the HTTP/2 (TLS) benchmark servers.
 * Hostname verification still applies; the certificates are issued for `localhost` and `127.0.0.1`.
 */
val trustAllManager: X509TrustManager = object : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

val trustAllSslContext: SSLContext = SSLContext.getInstance("TLS").apply {
    init(null, arrayOf(trustAllManager), null)
}
