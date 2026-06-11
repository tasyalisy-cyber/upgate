package qa.upgate.model

import com.fasterxml.jackson.annotation.JsonFormat

data class ErrorResponse(
    val success: Boolean,
    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    val message: List<String> = emptyList(),
)
