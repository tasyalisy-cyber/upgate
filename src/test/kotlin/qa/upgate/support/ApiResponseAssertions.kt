package qa.upgate.support

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import qa.upgate.api.ApiResponse
import qa.upgate.api.bodyAs
import qa.upgate.model.CreateUserResponse
import qa.upgate.model.ErrorResponse
import qa.upgate.model.UserDto
import qa.upgate.model.UserPayload
import java.time.LocalDateTime

private val testObjectMapper = jacksonObjectMapper()

private val expectedUserSchema: Map<String, JsonNode.() -> Boolean> = mapOf(
    "id" to JsonNode::isNumber,
    "username" to JsonNode::isTextual,
    "email" to JsonNode::isTextual,
    "created_at" to JsonNode::isTextual,
    "updated_at" to JsonNode::isTextual,
)

fun ApiResponse.assertSuccessAndGetDetails(expectedUser: UserPayload? = null): UserDto {
    assertThat(statusCode).isEqualTo(200)
    assertJsonContentType()
    val responseBody = bodyAs<CreateUserResponse>()
    val details = requireNotNull(responseBody.details) { "Created user details must be returned" }

    assertSoftly { softly ->
        softly.assertThat(responseBody.success).describedAs("Create response success").isTrue()
        softly.assertThat(responseBody.message)
            .describedAs("Success response message should describe user creation")
            .isNotBlank()
            .containsIgnoringCase("user")
            .containsIgnoringCase("created")
        softly.assertThat(details.id).describedAs("Created user id").isNotNull()
        softly.assertThat(details.createdAt).describedAs("Created user created_at").isNotBlank()
        softly.assertThat(details.updatedAt).describedAs("Created user updated_at").isNotBlank()

        expectedUser?.let { user ->
            softly.assertThat(details.username).describedAs("Created user username").isEqualTo(user.username)
            softly.assertThat(details.email).describedAs("Created user email").isEqualTo(user.email)
            softly.assertThat(details.password)
                .describedAs("API must not return plaintext password")
                .isNotEqualTo(user.password)
        }
    }

    return details
}

fun ApiResponse.assertValidationError(vararg expectedMessageTerms: String) {
    assertThat(statusCode).isEqualTo(400)
    assertJsonContentType()
    val responseBody = bodyAs<ErrorResponse>()
    val messageText = responseBody.message.toString()

    assertSoftly { softly ->
        softly.assertThat(responseBody.success).isFalse()
        softly.assertThat(responseBody.message)
            .describedAs("Validation response must include an error message, actual body: $body")
            .isNotEmpty()
        expectedMessageTerms.forEach { term ->
            softly.assertThat(messageText)
                .describedAs("Validation message should mention '$term', actual body: $body")
                .containsIgnoringCase(term)
        }
    }
}

fun ApiResponse.assertJsonContentType() {
    assertThat(contentType.lowercase())
        .describedAs("Expected JSON response, got '$contentType' with body: $body")
        .startsWith("application/json")
}

fun ApiResponse.assertGetUsersResponseSchema(): List<UserDto> {
    assertJsonContentType()

    val root = testObjectMapper.readTree(body)
    assertThat(root.isArray)
        .describedAs("GET /user/get response body must be a JSON array, actual body: $body")
        .isTrue()

    root.forEachIndexed { index, userNode ->
        assertUserJsonSchema(userNode, index)
    }

    return bodyAs<List<UserDto>>()
}

private fun assertUserJsonSchema(userNode: JsonNode, index: Int) {
    assertThat(userNode.isObject)
        .describedAs("GET /user/get item #$index must be a JSON object: $userNode")
        .isTrue()

    assertSoftly { softly ->
        expectedUserSchema.forEach { (fieldName, typeCheck) ->
            val fieldNode = userNode.path(fieldName)

            softly.assertThat(fieldNode.isMissingNode)
                .describedAs("GET /user/get item #$index must contain '$fieldName': $userNode")
                .isFalse()

            if (!fieldNode.isMissingNode && !fieldNode.isNull) {
                softly.assertThat(fieldNode.typeCheck())
                    .describedAs("GET /user/get item #$index field '$fieldName' has incorrect type: $fieldNode")
                    .isTrue()
            }
        }
    }
}

fun String?.parseApiTimestamp(fieldName: String): LocalDateTime {
    val timestamp = requireNotNull(this) { "$fieldName must be returned" }
    assertThat(timestamp).describedAs("$fieldName must be returned").isNotBlank()
    return runCatching { LocalDateTime.parse(timestamp, TestConfig.apiTimestampFormatter) }
        .getOrElse { error("$fieldName must match format yyyy-MM-dd HH:mm:ss, actual value: $timestamp") }
}
