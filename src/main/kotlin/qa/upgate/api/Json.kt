package qa.upgate.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

internal val objectMapper = jacksonObjectMapper()

internal inline fun <reified T> ApiResponse.bodyAs(): T = objectMapper.readValue(body)
