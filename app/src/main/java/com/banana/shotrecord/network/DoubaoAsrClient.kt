package com.banana.shotrecord.network

import android.util.Base64
import com.banana.shotrecord.data.AsrSettings
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * 火山引擎「豆包语音」录音文件极速版识别 HTTP 接口封装。
 * 文档: https://www.volcengine.com/docs/DoubaoVoice/LargemodelrecordingfileLiterecognitionAPI
 *
 * 接口为一次请求同步返回;若返回"处理中"状态码则由本类内部重试。
 */
object DoubaoAsrClient {

    private const val ENDPOINT =
        "https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash"

    /** 状态码见文档:20000000 成功,20000001/20000002 处理中,20000003 纯静音 */
    private const val CODE_SUCCESS = "20000000"
    private const val CODE_PROCESSING = "20000001"
    private const val CODE_QUEUED = "20000002"
    private const val CODE_SILENCE = "20000003"

    private const val MAX_ATTEMPTS = 8
    private const val RETRY_INTERVAL_MS = 3000L
    private const val MAX_FILE_BYTES = 90L * 1024 * 1024

    /** 转写成功返回文本;失败返回 Result.failure,消息可直接展示 */
    fun transcribe(file: File, settings: AsrSettings): Result<String> {
        if (!settings.isConfigured) {
            return Result.failure(IllegalStateException("未配置转写服务"))
        }
        if (file.length() > MAX_FILE_BYTES) {
            return Result.failure(IllegalArgumentException("录音文件过大,暂不支持超过 90MB 的录音"))
        }

        repeat(MAX_ATTEMPTS) { attempt ->
            val (statusCode, message, body) = post(file, settings)
            when (statusCode) {
                CODE_SUCCESS -> {
                    val text = runCatching {
                        JSONObject(body).getJSONObject("result").getString("text")
                    }.getOrElse { "" }
                    return Result.success(text.ifBlank { "(未识别到内容)" })
                }
                CODE_SILENCE -> return Result.success("(这段录音里没有检测到人声)")
                CODE_PROCESSING, CODE_QUEUED -> {
                    // 极速版一般不会出现,出现则按文档"什么都不做"并在间隔后重试
                    if (attempt == MAX_ATTEMPTS - 1) {
                        return Result.failure(RuntimeException("转写处理超时($message)"))
                    }
                    Thread.sleep(RETRY_INTERVAL_MS)
                }
                else -> return Result.failure(RuntimeException("转写失败[$statusCode] $message"))
            }
        }
        return Result.failure(RuntimeException("转写处理超时"))
    }

    private data class Response(val statusCode: String?, val message: String, val body: String)

    private fun post(file: File, settings: AsrSettings): Response {
        val payload = JSONObject().apply {
            put("user", JSONObject().put("uid", "shotrecord"))
            put("audio", JSONObject().put(
                "data",
                Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            ))
            put(
                "request",
                JSONObject()
                    .put("model_name", "bigmodel")
                    .put("enable_itn", true)
                    .put("enable_punc", true)
                    .put("enable_ddc", true)
            )
        }

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 15_000
                readTimeout = 120_000
                setRequestProperty("Content-Type", "application/json")
                if (settings.useLegacyAuth) {
                    setRequestProperty("X-Api-App-Key", settings.appKey)
                    setRequestProperty("X-Api-Access-Key", settings.accessToken)
                } else {
                    setRequestProperty("X-Api-Key", settings.appKey)
                }
                setRequestProperty("X-Api-Resource-Id", settings.resourceId)
                setRequestProperty("X-Api-Request-Id", UUID.randomUUID().toString())
                setRequestProperty("X-Api-Sequence", "-1")
            }
            connection.outputStream.use { out ->
                out.write(payload.toString().toByteArray(Charsets.UTF_8))
                out.flush()
            }
            val statusCode = connection.getHeaderField("X-Api-Status-Code")
            val message = connection.getHeaderField("X-Api-Message") ?: ""
            val body = readBody(connection)
            Response(statusCode, message, body)
        } catch (e: Exception) {
            Response(null, e.message ?: "网络请求失败", "")
        } finally {
            connection?.disconnect()
        }
    }

    private fun readBody(connection: HttpURLConnection): String = try {
        connection.inputStream.bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        try {
            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
