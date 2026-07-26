package top.yogiczy.mytv.tv.ui.screens.videoplayer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.material.CircularProgressIndicator
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme

@Composable
fun VideoPlayerError(
    modifier: Modifier = Modifier,
    errorProvider: () -> String? = { null },
    retryMessageProvider: () -> String? = { null },
) {
    val retryMessage = retryMessageProvider()
    val error = errorProvider()
    if (retryMessage == null && error == null) return

    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                shape = MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (retryMessage != null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .size(30.dp),
                strokeWidth = 3.dp,
            )
            Text(
                text = retryMessage,
                style = MaterialTheme.typography.titleLarge,
            )
        } else if (error != null) {
            Text(
                text = "播放失敗",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
            )

            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = 0.8f),
            )

            getErrorCodeDesc(error)?.let { nnDesc ->
                Text(
                    text = nnDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.8f),
                )
            }
        }
    }
}

private fun getErrorCodeDesc(error: String): String? {
    return when (error.split("(").first()) {
        "UNSUPPORTED_TYPE" -> "不支持的視頻類型，請檢查視頻源。"
        "LOAD_TIMEOUT" -> "加載超時，請檢查網絡連接或視頻源。"
        "ERROR_CODE_UNSPECIFIED" -> "未知錯誤，請稍後再試。"
        "ERROR_CODE_REMOTE_ERROR" -> "遠程錯誤，請稍後再試。"
        "ERROR_CODE_BEHIND_LIVE_WINDOW" -> "實時窗口錯誤，請稍後再試。"
        "ERROR_CODE_TIMEOUT" -> "超時錯誤，請稍後再試。"
        "ERROR_CODE_IO_UNSPECIFIED" -> "IO錯誤，請稍後再試。"
        "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED" -> "網絡連接異常或不支持IPv6，請檢查網絡設置。"
        "ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT" -> "網絡連接超時，請檢查網絡設置。"
        "ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE" -> "HTTP請求返回錯誤類型，請檢查視頻源。"
        "ERROR_CODE_IO_BAD_HTTP_STATUS" -> "HTTP請求返回錯誤狀態，請檢查視頻源。"
        "ERROR_CODE_IO_FILE_NOT_FOUND" -> "視頻文件未找到，請檢查視頻源。"
        "ERROR_CODE_IO_NO_PERMISSION" -> "沒有權限訪問視頻文件，請檢查權限設置。"
        "ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED" -> "不允許使用明文訪問視頻文件，請檢查權限設置。"
        "ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE" -> "視頻文件讀取位置超出範圍，請檢查視頻源。"
        "ERROR_CODE_PARSING_CONTAINER_MALFORMED" -> "視頻容器格式錯誤，請檢查視頻源。"
        "ERROR_CODE_PARSING_MANIFEST_MALFORMED" -> "視頻流清單格式錯誤，請檢查視頻源。"
        "ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED" -> "不支持的視頻容器類型，請檢查視頻源。"
        "ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED" -> "不支持的視頻流清單類型，請檢查視頻源。"
        "ERROR_CODE_DECODER_INIT_FAILED" -> "視頻解碼器初始化失敗，請檢查視頻源。"
        "ERROR_CODE_DECODER_QUERY_FAILED" -> "視頻解碼器查詢失敗，請檢查視頻源。"
        "ERROR_CODE_DECODING_FAILED" -> "視頻解碼失敗，請檢查視頻源。"
        "ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES" -> "視頻解碼格式超出能力範圍，請檢查視頻源。"
        "ERROR_CODE_DECODING_FORMAT_UNSUPPORTED" -> "不支持的視頻解碼格式，請檢查視頻源。"
        "ERROR_CODE_DECODING_RESOURCES_RECLAIMED" -> "視頻解碼資源被回收，請檢查視頻源。"
        "ERROR_CODE_AUDIO_TRACK_INIT_FAILED" -> "音頻軌道初始化失敗，請檢查視頻源。"
        "ERROR_CODE_AUDIO_TRACK_WRITE_FAILED" -> "音頻軌道寫入失敗，請檢查視頻源。"
        "ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED" -> "音頻軌道離線寫入失敗，請檢查視頻源。"
        "ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED" -> "音頻軌道離線初始化失敗，請檢查視頻源。"
        "ERROR_CODE_VIDEO_FRAME_PROCESSOR_INIT_FAILED" -> "視頻幀處理器初始化失敗，請檢查視頻源。"
        "ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED" -> "視頻幀處理失敗，請檢查視頻源。"
        "ERROR_CODE_FAILED_RUNTIME_CHECK" -> "運行時檢查失敗，請檢查視頻源。"
        else -> null
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun VideoPlayerErrorPreview() {
    MyTVTheme {
        VideoPlayerError(
            errorProvider = { "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED(2001)" }
        )
    }
}
