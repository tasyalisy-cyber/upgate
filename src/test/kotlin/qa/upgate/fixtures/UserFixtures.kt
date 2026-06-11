package qa.upgate.fixtures

import qa.upgate.api.UserApi
import qa.upgate.model.UserDto
import qa.upgate.model.UserPayload
import qa.upgate.support.assertSuccessAndGetDetails
import java.util.UUID

object UserFixtures {
    fun randomPayload(
        username: String = "autotest_${UUID.randomUUID().toString().replace("-", "").take(12)}",
        email: String = "$username@example.com",
        password: String = "Passw0rd!",
    ): UserPayload = UserPayload(
        username = username,
        email = email,
        password = password,
    )
}

data class CreatedUserFixture(
    val payload: UserPayload,
    val details: UserDto,
)

fun UserApi.createUserFixture(user: UserPayload = UserFixtures.randomPayload()): CreatedUserFixture {
    val details = createUserAsMultipart(user).assertSuccessAndGetDetails(user)
    return CreatedUserFixture(user, details)
}
