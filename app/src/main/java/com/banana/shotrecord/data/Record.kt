package com.banana.shotrecord.data

/** 一条语音记录 */
data class Record(
    val id: Long,
    val filePath: String,
    val durationMs: Long,
    val createdAt: Long,
    /** 语音转写结果,暂未实现转写时为 null */
    val transcript: String?,
)
