package qa.upgate.api

import java.net.http.HttpHeaders

data class ApiResponse(
    val statusCode: Int,
    val contentType: String,
    val headers: HttpHeaders,
    val body: String,
) {
    fun header(name: String): String? = headers.firstValue(name).orElse(null)
}
