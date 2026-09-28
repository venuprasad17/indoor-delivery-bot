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

    fun parse(throwable: Throwable): ParsedError {
        return when (throwable) {
            is HttpException -> {
                val code = throwable.code()
                val errorBody = try {
                    throwable.response()?.errorBody()?.string()
                } catch (e: Exception) {
                    null
                }
                parseHttpError(code, errorBody)
            }
            is IOException -> ParsedError(
                message = "Cannot reach server. Retrying.",
                statusCode = -1
            )
            else -> ParsedError(
                message = throwable.localizedMessage ?: "Unexpected error",
                statusCode = -2
            )
        }
    }

    fun parseHttpError(statusCode: Int, errorBody: String?): ParsedError {
        if (statusCode == 429) {
            return ParsedError("Too many attempts. Try again later.", errorCode = "RATE_LIMITED", statusCode = 429)
        }
        if (statusCode == 501) {
            return ParsedError("Not available yet on the server", errorCode = "NOT_IMPLEMENTED", statusCode = 501)
        }
        if (statusCode == 423) {
            return ParsedError("Locked by emergency stop", errorCode = "LOCKED", statusCode = 423)
        }

        if (errorBody.isNullOrBlank()) {
            val msg = when (statusCode) {
                401 -> "Unauthorized. Please sign in again."
                403 -> "This app is for administrators"
                404 -> "Not found on server"
                409 -> "Resource conflict"
                500 -> "Server internal error"
                else -> "HTTP $statusCode error"
            }
            return ParsedError(msg, statusCode = statusCode)
        }

        return try {
            val json = JSONObject(errorBody)

            // Case 1: "detail" field
            if (json.has("detail")) {
                val detailObj = json.optJSONObject("detail")
                if (detailObj != null) {
                    val error = detailObj.optString("error", "")
                    val message = detailObj.optString("message", "")
                    val activeSessionId = detailObj.optString("active_session_id", "").ifEmpty { null }
                    val currentState = detailObj.optString("current_state", "")
                    val requested = detailObj.optString("requested", "")

                    val msg = when {
                        error == "SESSION_ACTIVE" -> "Another session is active"
                        error == "INVALID_TRANSITION" && currentState.isNotEmpty() ->
                            "Cannot transition session from $currentState to $requested"
                        message.isNotEmpty() -> message
                        error.isNotEmpty() -> error
                        else -> "Request rejected by server"
                    }
                    return ParsedError(msg, errorCode = error, activeSessionId = activeSessionId, statusCode = statusCode)
                } else {
                    val detailStr = json.optString("detail", "")
                    if (detailStr.isNotEmpty()) {
                        return ParsedError(detailStr, statusCode = statusCode)
                    }
                }
            }

            // Case 2: Top-level error / message
            val topError = json.optString("error", "")
            val topMessage = json.optString("message", "")

            val msg = when {
                topError == "NO_ROBOT_REGISTERED" -> "No robot registered"
                topError == "ROBOT_NOT_AUTONOMOUS" -> "Robot is not in autonomous mode"
                topError == "ROBOT_OFFLINE" -> "Robot is offline"
                topError == "HOME_PROTECTED" -> "Cannot delete Home destination"
                topError == "ACTIVE_DELIVERY" -> "Cannot modify while delivery is active"
                topError == "RESET_FAILED" -> if (topMessage.isNotEmpty()) topMessage else "Emergency stop reset failed"
                topMessage.isNotEmpty() -> topMessage
                topError.isNotEmpty() -> topError
                else -> "HTTP $statusCode error"
            }
            ParsedError(msg, errorCode = topError, statusCode = statusCode)
        } catch (e: Exception) {
            ParsedError("Server returned code $statusCode", statusCode = statusCode)
        }
    }
}
