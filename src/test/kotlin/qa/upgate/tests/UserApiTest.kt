package qa.upgate.tests

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import qa.upgate.api.UserApi
import qa.upgate.api.bodyAs
import qa.upgate.diagnostics.ApiDiagnostics
import qa.upgate.diagnostics.ApiDiagnosticsExtension
import qa.upgate.fixtures.UserFixtures
import qa.upgate.fixtures.createUserFixture
import qa.upgate.model.CreateUserResponse
import qa.upgate.model.UserDto
import qa.upgate.model.UserPayload
import qa.upgate.support.TestConfig
import qa.upgate.support.assertGetUsersResponseSchema
import qa.upgate.support.assertSuccessAndGetDetails
import qa.upgate.support.assertValidationError
import qa.upgate.support.parseApiTimestamp
import java.util.stream.Stream

class UserApiTest {
    private val api = UserApi(
        baseUrl = TestConfig.baseUrl,
        exchangeRecorder = ApiDiagnostics::record,
    )

    @JvmField
    @RegisterExtension
    val apiDiagnostics = ApiDiagnosticsExtension()

    @Nested
    inner class GetUsers {
        @Test
        fun `returns json array with user schema`() {
            api.createUserFixture()
            val response = api.getUsers()
            assertThat(response.statusCode).isEqualTo(200)
            val users = response.assertGetUsersResponseSchema()
            assertThat(users)
                .describedAs("GET /user/get response should contain at least one user for schema validation")
                .isNotEmpty()
        }

        @Disabled("Requirement gap / security concern: GET /user/get currently exposes the user list without authentication")
        @Test
        fun `requires authentication`() {
            val response = api.getUsers()
            assertThat(response.statusCode).isIn(401, 403)
        }

        @Disabled("Known defect: query parameters currently can produce 500 responses with SQL details")
        @Test
        fun `handles suspicious query parameters without sql errors`() {
            val response = api.getUsersWithQuery("page=1%27%20OR%20%271%27%3D%271&limit=1")
            assertSoftly { softly ->
                softly.assertThat(response.statusCode)
                    .describedAs("Suspicious query parameters must not cause server-side SQL errors")
                    .isLessThan(500)
                softly.assertThat(response.body.lowercase())
                    .doesNotContain("sqlite_error", "select * from")
            }
        }
    }

    @Nested
    inner class CreateUser {
        @Test
        fun `succeeds with multipart form data and appears in user list`() {
            val user = UserFixtures.randomPayload()

            val createdDetails = api.createUserAsMultipart(user).assertSuccessAndGetDetails(user)
            val createdId = requireNotNull(createdDetails.id) { "Created user id must be returned" }

            val users = api.getUsers().assertGetUsersResponseSchema()
            val listedUser = users.singleOrNull { it.id == createdId }

            assertSoftly { softly ->
                softly.assertThat(listedUser)
                    .describedAs("Created user must be present in GET /user/get response")
                    .isNotNull()
                softly.assertThat(listedUser?.username).isEqualTo(user.username)
                softly.assertThat(listedUser?.email).isEqualTo(user.email)
            }
        }

        @Test
        fun `succeeds with raw json body`() {
            val user = UserFixtures.randomPayload()
            api.createUserAsJson(user).assertSuccessAndGetDetails(user)
        }

        @Test
        fun `returns valid creation timestamps`() {
            val (_, details) = api.createUserFixture()

            val createdAt = details.createdAt.parseApiTimestamp("created_at")
            val updatedAt = details.updatedAt.parseApiTimestamp("updated_at")

            assertThat(updatedAt)
                .describedAs("Newly created user must have updated_at equal to created_at")
                .isEqualTo(createdAt)
        }

        @Test
        fun `returns positive unique ids`() {
            val (_, firstDetails) = api.createUserFixture()
            val (_, secondDetails) = api.createUserFixture()
            val firstId = requireNotNull(firstDetails.id) { "First created user id must be returned" }
            val secondId = requireNotNull(secondDetails.id) { "Second created user id must be returned" }

            assertSoftly { softly ->
                softly.assertThat(firstId).describedAs("First created user id must be positive").isPositive()
                softly.assertThat(secondId).describedAs("Second created user id must be positive").isPositive()
                softly.assertThat(secondId).describedAs("Created user ids must be unique").isNotEqualTo(firstId)
            }
        }

        @ParameterizedTest(name = "rejects invalid required field: {0}")
        @MethodSource("qa.upgate.tests.UserApiTest#invalidRequiredFieldCases")
        fun `rejects invalid required fields`(
            caseName: String,
            fieldName: String,
            invalidValue: String?,
        ) {
            val baseUser = UserFixtures.randomPayload()
            val invalidPayload = baseUser.withFieldValue(fieldName, invalidValue)

            api.createUserAsMultipart(invalidPayload).assertValidationError(fieldName)
        }

        @ParameterizedTest(name = "rejects duplicate unique field: {0}")
        @MethodSource("qa.upgate.tests.UserApiTest#duplicateUniqueFieldCases")
        fun `rejects duplicate unique fields`(fieldName: String) {
            val (original) = api.createUserFixture()
            val duplicatePayload = UserFixtures.randomPayload().withFieldValue(
                fieldName = fieldName,
                value = when (fieldName) {
                    "username" -> original.username
                    "email" -> original.email
                    else -> error("Unsupported unique field: $fieldName")
                },
            )
            val duplicate = api.createUserAsMultipart(duplicatePayload)

            duplicate.assertValidationError(fieldName)
        }

        @Disabled("Known defect: API currently accepts invalid email format with 200 OK")
        @Test
        fun `rejects invalid email format`() {
            api.createUserAsMultipart(UserFixtures.randomPayload(email = "not-an-email"))
                .assertValidationError("email")
        }
    }

    @Nested
    inner class Security {
        @Disabled("Known defect: API currently exposes password hashes in create and get responses")
        @Test
        fun `public responses should not expose password hash`() {
            val createResponse = api.createUserAsMultipart(UserFixtures.randomPayload())
            val createBody = createResponse.bodyAs<CreateUserResponse>()

            val users = api.getUsers().bodyAs<List<UserDto>>()

            assertSoftly { softly ->
                softly.assertThat(createBody.details?.password).isNull()
                softly.assertThat(users).allSatisfy {
                    assertThat(it.password).isNull()
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun invalidRequiredFieldCases(): Stream<Arguments> = Stream.of(
            Arguments.of("missing username", "username", null as String?),
            Arguments.of("missing email", "email", null as String?),
            Arguments.of("missing password", "password", null as String?),
            Arguments.of("empty username", "username", ""),
            Arguments.of("empty email", "email", ""),
            Arguments.of("empty password", "password", ""),
        )

        @JvmStatic
        fun duplicateUniqueFieldCases(): Stream<Arguments> = Stream.of(
            Arguments.of("username"),
            Arguments.of("email"),
        )
    }
}

private fun UserPayload.withFieldValue(fieldName: String, value: String?): UserPayload =
    when (fieldName) {
        "username" -> copy(username = value)
        "email" -> copy(email = value)
        "password" -> copy(password = value)
        else -> error("Unsupported required field: $fieldName")
    }
