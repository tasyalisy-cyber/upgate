package qa.upgate.api

import qa.upgate.model.UserPayload
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

class UserApi(
    baseUrl: String,
    private val exchangeRecorder: (HttpExchange) -> Unit = {},
) {
    private val baseUrl = baseUrl.trimEnd('/')
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    fun getUsers(): ApiResponse {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/user/get"))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        return send(request)
    }

    fun getUsersWithQuery(rawQuery: String): ApiResponse {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/user/get?$rawQuery"))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        return send(request)
    }

    fun createUserAsMultipart(user: UserPayload): ApiResponse {
        val boundary = "----upgate-test-${UUID.randomUUID()}"
        val body = MultipartBodyBuilder.build(
            boundary,
            listOfNotNull(
                user.username?.let { "username" to it },
                user.email?.let { "email" to it },
                user.password?.let { "password" to it },
            ),
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/user/create"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()

        return send(request, body)
    }

    fun createUserAsJson(user: UserPayload): ApiResponse {
        val body = objectMapper.writeValueAsString(
            listOfNotNull(
                user.username?.let { "username" to it },
                user.email?.let { "email" to it },
                user.password?.let { "password" to it },
            ).toMap(),
        )

        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/user/create"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build()

        return send(request, body)
    }

    private fun send(request: HttpRequest, requestBody: String? = null): ApiResponse {
        val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        val apiResponse = ApiResponse(
            statusCode = response.statusCode(),
            contentType = response.headers().firstValue("Content-Type").orElse(""),
            headers = response.headers(),
            body = response.body(),
        )

        exchangeRecorder(
            HttpExchange(
                method = request.method(),
                uri = request.uri().toString(),
                requestHeaders = request.headers().map(),
                requestBody = requestBody,
                statusCode = apiResponse.statusCode,
                responseHeaders = response.headers().map(),
                responseBody = apiResponse.body,
            ),
        )

        return apiResponse
    }
}
