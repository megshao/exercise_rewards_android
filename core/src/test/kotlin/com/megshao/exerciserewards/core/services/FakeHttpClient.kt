package com.megshao.exerciserewards.core.services

import com.megshao.exerciserewards.core.models.AppError
import com.megshao.exerciserewards.core.networking.HTTPClienting
import com.megshao.exerciserewards.core.networking.HTTPFormResult

/**
 * 可程式化每個 path 回應的假 [HTTPClienting]，供 service 層的測試使用。不打真實網路。
 */
class FakeHttpClient : HTTPClienting {
    val htmlByPath: MutableMap<String, String> = mutableMapOf()
    val formResultByPath: MutableMap<String, HTTPFormResult> = mutableMapOf()
    val redirectLocationByPath: MutableMap<String, String> = mutableMapOf()
    val uploadResultByPath: MutableMap<String, HTTPFormResult> = mutableMapOf()

    val getPaths: MutableList<String> = mutableListOf()
    val postPaths: MutableList<String> = mutableListOf()
    val postFields: MutableMap<String, List<Pair<String, String>>> = mutableMapOf()
    val redirectPaths: MutableList<String> = mutableListOf()
    val uploadPaths: MutableList<String> = mutableListOf()
    val uploadFileFields: MutableList<String> = mutableListOf()
    var resetSessionCallCount: Int = 0
        private set

    override suspend fun getHtml(path: String): String {
        getPaths.add(path)
        return htmlByPath[path] ?: throw AppError.UnexpectedResponse(404)
    }

    override suspend fun postForm(path: String, fields: List<Pair<String, String>>): HTTPFormResult {
        postPaths.add(path)
        postFields[path] = fields
        return formResultByPath[path] ?: throw AppError.UnexpectedResponse(404)
    }

    /** 非 3xx（或未事先程式化）一律回 null，對齊真實 `redirectLocation` 的語意。 */
    override suspend fun redirectLocation(path: String): String? {
        redirectPaths.add(path)
        return redirectLocationByPath[path]
    }

    override suspend fun uploadMultipart(
        path: String,
        fields: List<Pair<String, String>>,
        fileField: String,
        fileName: String,
        mimeType: String,
        fileData: ByteArray,
    ): HTTPFormResult {
        uploadPaths.add(path)
        uploadFileFields.add(fileField)
        return uploadResultByPath[path] ?: throw AppError.UnexpectedResponse(404)
    }

    override suspend fun resetSession() {
        resetSessionCallCount += 1
    }
}

/** 只含一個 `_csrf` 隱藏欄位的最小頁面。 */
internal fun csrfHtml(csrf: String): String = """<input type="hidden" name="_csrf" value="$csrf"/>"""
