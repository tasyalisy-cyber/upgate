package qa.upgate.api

data class HttpExchange(
    val method: String,
    val uri: String,
    val requestHeaders: Map<String, List<String>>,
    val requestBody: String?,
    val statusCode: Int,
    val responseHeaders: Map<String, List<String>>,
    val responseBody: String,
)
