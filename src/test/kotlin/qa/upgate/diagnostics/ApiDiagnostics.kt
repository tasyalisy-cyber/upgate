package qa.upgate.diagnostics

import qa.upgate.api.HttpExchange

object ApiDiagnostics {
    private val exchanges = ThreadLocal.withInitial { mutableListOf<HttpExchange>() }

    fun record(exchange: HttpExchange) {
        exchanges.get().add(exchange)
    }

    fun clear() {
        exchanges.get().clear()
    }

    fun dump(): String = buildString {
        appendLine("HTTP exchanges captured before test failure:")
        exchanges.get().forEachIndexed { index, exchange ->
            appendLine("[$index] ${exchange.method} ${exchange.uri}")
            appendLine("Request headers: ${exchange.requestHeaders}")
            exchange.requestBody?.let { appendLine("Request body: ${redactAndTruncate(it)}") }
            appendLine("Response status: ${exchange.statusCode}")
            appendLine("Response headers: ${exchange.responseHeaders}")
            appendLine("Response body: ${redactAndTruncate(exchange.responseBody)}")
        }
    }

    private fun redactAndTruncate(body: String): String =
        body
            .replace(Regex("(?i)(\"password\"\\s*:\\s*\")[^\"]*(\")"), "$1<redacted>$2")
            .replace(Regex("(?s)(name=\"password\"\\r?\\n\\r?\\n).*?(\\r?\\n--)"), "$1<redacted>$2")
            .let { if (it.length > 2_000) it.take(2_000) + "... <truncated>" else it }
}
