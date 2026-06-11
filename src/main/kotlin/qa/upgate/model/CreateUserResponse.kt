package qa.upgate.model

data class CreateUserResponse(
    val success: Boolean,
    val details: UserDto?,
    val message: String?,
)
