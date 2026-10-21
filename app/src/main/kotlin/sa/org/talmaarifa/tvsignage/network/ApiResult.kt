package sa.org.talmaarifa.tvsignage.network

import com.squareup.moshi.Moshi
import retrofit2.HttpException
import sa.org.talmaarifa.tvsignage.network.dto.ApiErrorBodyDto
import java.io.IOException

/** Known `code` values from docs/tv-device-api.md, grouped by what the app must do about them. */
object ApiErrorCode {
    const val UNAUTHENTICATED = "UNAUTHENTICATED"
    const val MODULE_DISABLED = "MODULE_DISABLED"
    const val VALIDATION_FAILED = "VALIDATION_FAILED"
    const val NOT_FOUND = "NOT_FOUND"
    const val ATTACHMENT_INFECTED = "ATTACHMENT_INFECTED"
    const val ATTACHMENT_NOT_SCANNED = "ATTACHMENT_NOT_SCANNED"
    const val ATTACHMENT_FILE_MISSING = "ATTACHMENT_FILE_MISSING"
}

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    /** The server answered with a non-2xx status and a parsed (or unparseable) error envelope. */
    data class ApiError(val httpStatus: Int, val code: String?, val messageAr: String?) : ApiResult<Nothing>
    /** No HTTP response at all — offline, DNS failure, timeout, TLS failure, etc. */
    data class NetworkError(val cause: IOException) : ApiResult<Nothing>
}

/** True for 401 UNAUTHENTICATED: the stored credential is wrong or revoked — wipe it and re-pair. */
fun ApiResult.ApiError.isCredentialInvalid(): Boolean = httpStatus == 401 && code == ApiErrorCode.UNAUTHENTICATED

/** True for 409 MODULE_DISABLED: a known, recoverable, admin-controlled pause — not a device fault. */
fun ApiResult.ApiError.isModuleDisabled(): Boolean = httpStatus == 409 && code == ApiErrorCode.MODULE_DISABLED

class ApiCaller(private val moshi: Moshi) {
    private val errorAdapter = moshi.adapter(ApiErrorBodyDto::class.java)

    suspend fun <T> call(block: suspend () -> T): ApiResult<T> = try {
        ApiResult.Success(block())
    } catch (e: HttpException) {
        val body = e.response()?.errorBody()?.string()
        val parsed = body?.let { runCatching { errorAdapter.fromJson(it) }.getOrNull() }
        ApiResult.ApiError(e.code(), parsed?.code, parsed?.messageAr)
    } catch (e: IOException) {
        ApiResult.NetworkError(e)
    }
}
