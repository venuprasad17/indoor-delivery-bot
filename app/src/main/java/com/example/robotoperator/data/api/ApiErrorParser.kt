package com.example.robotoperator.data.api

import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException

object ApiErrorParser {

    data class ParsedError(
        val message: String,
        val errorCode: String? = null,
        val activeSessionId: String? = null,
        val statusCode: Int = 0
    )

    fun parseErrorMessage(throwable: Throwable): String {
        return parse(throwable).message
    }

    fun parse(throwable: Throwable): ParsedError {
        return when (throwable) {
            is HttpException -> {
                val code = throwable.code()
                if (code == 429) {
                    return ParsedError(
                        message = "Too many attempts, try again later.",
                        statusCode = 429
                    )
                }

                if (code == 501) {
                    return ParsedError(
                        message = "Not available yet on the server",
                        statusCode = 501
                    )
                }

                val errorBody = try {
                    throwable.response()?.errorBody()?.string()
                } catch (e: Exception) {
                    null
                }

                if (errorBody.isNullOrBlank()) {
                    return ParsedError(
                        message = when (code) {
                            400 -> "Bad request"
                            401 -> "Authentication required"
                            403 -> "This app is for administrators"
                            404 -> "Resource not found"
                            409 -> "Resource conflict"
                            422 -> "Unprocessable entity"
                            423 -> "Robot locked because of emergency stop"
                            500 -> "Server error"
                            else -> "Request failed with code $code"
                        },
                        statusCode = code
                    )
                }

                parseErrorBody(errorBody, code)
            }
            is IOException -> {
                ParsedError(
                    message = "Cannot reach server. Retrying.",
                    statusCode = -1
                )
            }
            else -> {
                ParsedError(
                    message = throwable.localizedMessage ?: "Unexpected error",
                    statusCode = -2
                )
            }
        }
    }

    private fun parseErrorBody(jsonStr: String, statusCode: Int): ParsedError {
        return try {
            val json = JSONObject(jsonStr)

            val directError = if (json.has("error")) json.optString("error") else null
            val directMessage = if (json.has("message")) json.optString("message") else null

            var detailError: String? = null
            var detailMessage: String? = null
            var activeSessionId: String? = null
            var currentState: String? = null
            var requestedState: String? = null

            if (json.has("detail")) {
                val detailObj = json.optJSONObject("detail")
                if (detailObj != null) {
                    detailError = detailObj.optString("error", null)
                    detailMessage = detailObj.optString("message", null)
                    if (detailObj.has("active_session_id")) {
                        activeSessionId = detailObj.optString("active_session_id")
                    }
                    if (detailObj.has("current_state")) {
                        currentState = detailObj.optString("current_state")
                    }
                    if (detailObj.has("requested")) {
                        requestedState = detailObj.optString("requested")
                    }
                } else {
                    detailMessage = json.optString("detail")
                }
            }

            val finalErrorCode = directError ?: detailError

            val finalMessage = when {
                !directMessage.isNullOrBlank() -> directMessage
                !detailMessage.isNullOrBlank() -> detailMessage
                finalErrorCode == "EMAIL_ALREADY_EXISTS" -> "Email already exists"
                finalErrorCode == "CANNOT_DELETE_SELF" -> "Cannot delete your own account"
                finalErrorCode == "NO_ROBOT_REGISTERED" -> "No robot registered"
                finalErrorCode == "RESET_FAILED" -> "Failed to reset emergency stop"
                finalErrorCode == "SESSION_ACTIVE" -> "Session already active: ${activeSessionId ?: ""}".trim()
                finalErrorCode == "INVALID_TRANSITION" -> "Invalid transition from ${currentState ?: "current"} to ${requestedState ?: "requested"}"
                finalErrorCode == "ROBOT_NOT_AUTONOMOUS" -> "Robot is not in autonomous mode"
                finalErrorCode == "ROBOT_OFFLINE" -> "Robot is offline"
                finalErrorCode == "ROBOT_NOT_READY" -> "Robot not ready for mode change"
                finalErrorCode == "LOCKED" -> "Robot locked because of emergency stop"
                finalErrorCode == "MAP_NOT_FOUND" -> "Map not found"
                finalErrorCode == "INVALID_COORDINATES" -> "Invalid coordinates"
                finalErrorCode == "HOME_PROTECTED" -> "Home destination is protected"
                finalErrorCode == "ACTIVE_DELIVERY" -> "Cannot modify destination during active delivery"
                statusCode == 423 -> "Robot locked because of emergency stop"
                statusCode == 404 -> "Resource not found"
                statusCode == 409 -> "Resource conflict"
                statusCode == 500 -> "Backend server error (HTTP 500). Please check backend logs on Render."
                !finalErrorCode.isNullOrBlank() -> finalErrorCode.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                else -> "Request failed with code $statusCode"
            }

            ParsedError(
                message = finalMessage,
                errorCode = finalErrorCode,
                activeSessionId = activeSessionId,
                statusCode = statusCode
            )
        } catch (e: Exception) {
            val fallbackMsg = if (statusCode == 500) {
                "Backend server error (HTTP 500). Please check backend logs on Render."
            } else if (statusCode == 404) {
                "Endpoint not found (HTTP 404)."
            } else if (jsonStr.isNotBlank()) {
                jsonStr.take(120)
            } else {
                "Request failed with status $statusCode"
            }
            ParsedError(
                message = fallbackMsg,
                statusCode = statusCode
            )
        }
    }
}
