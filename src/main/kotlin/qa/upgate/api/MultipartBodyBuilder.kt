package qa.upgate.api

/**
 * Minimal multipart builder for text fields used by this test suite.
 * Replace with a dedicated HTTP multipart library if file or binary parts are needed.
 */
internal object MultipartBodyBuilder {
    fun build(boundary: String, fields: List<Pair<String, String>>): String =
        buildString {
            fields.forEach { (name, value) ->
                append("--").append(boundary).append("\r\n")
                append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n")
                append(value).append("\r\n")
            }
            append("--").append(boundary).append("--\r\n")
        }
}
