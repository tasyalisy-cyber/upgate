package qa.upgate.model

import com.fasterxml.jackson.annotation.JsonProperty

data class UserDto(
    val id: Long?,
    val username: String?,
    val email: String?,
    val password: String?,
    @JsonProperty("created_at")
    val createdAt: String?,
    @JsonProperty("updated_at")
    val updatedAt: String?,
)
