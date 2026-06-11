package qa.upgate.support

import java.time.format.DateTimeFormatter

object TestConfig {
    val baseUrl: String = System.getProperty("baseUrl")?.takeIf { it.isNotBlank() }
        ?: System.getenv("BASE_URL")?.takeIf { it.isNotBlank() }
        ?: error("Set API base URL with -DbaseUrl=http://host:port or BASE_URL=http://host:port")

    val apiTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
}
