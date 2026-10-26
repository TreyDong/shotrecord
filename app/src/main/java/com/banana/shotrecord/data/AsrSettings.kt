package com.banana.shotrecord.data

import android.content.Context

/** 豆包(火山引擎)语音转文字的接入配置 */
class AsrSettings(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("asr_settings", Context.MODE_PRIVATE)

    /** 旧版控制台 = App ID;新版控制台 = App Key。两者都填到这一项 */
    var appKey: String
        get() = sp.getString(KEY_APP_KEY, "") ?: ""
        set(value) = sp.edit().putString(KEY_APP_KEY, value.trim()).apply()

    /** 旧版控制台的 Access Token;新版控制台留空即可 */
    var accessToken: String
        get() = sp.getString(KEY_ACCESS_TOKEN, "") ?: ""
        set(value) = sp.edit().putString(KEY_ACCESS_TOKEN, value.trim()).apply()

    /** 调用的服务资源 ID */
    var resourceId: String
        get() = sp.getString(KEY_RESOURCE_ID, DEFAULT_RESOURCE_ID) ?: DEFAULT_RESOURCE_ID
        set(value) = sp.edit().putString(KEY_RESOURCE_ID, value.trim()).apply()

    /** 录音结束后是否自动发起转写 */
    var autoTranscribe: Boolean
        get() = sp.getBoolean(KEY_AUTO_TRANSCRIBE, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_TRANSCRIBE, value).apply()

    val isConfigured: Boolean get() = appKey.isNotBlank()

    /** 旧版控制台需要 AppKey + AccessToken 双凭据;新版只需要 AppKey */
    val useLegacyAuth: Boolean get() = accessToken.isNotBlank()

    companion object {
        private const val KEY_APP_KEY = "app_key"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_RESOURCE_ID = "resource_id"
        private const val KEY_AUTO_TRANSCRIBE = "auto_transcribe"
        const val DEFAULT_RESOURCE_ID = "volc.bigasr.auc_turbo"
    }
}
