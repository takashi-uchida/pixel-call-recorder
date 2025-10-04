package com.callrecorder.pixel.error

/**
 * Sealed class hierarchy for all application errors.
 * Provides structured error handling throughout the call recorder app.
 */
sealed class CallRecorderError : Exception() {
    abstract val errorCode: String
    abstract val userMessage: String
}

/**
 * Permission-related errors
 */
sealed class PermissionError : CallRecorderError() {
    
    object MicrophonePermissionDenied : PermissionError() {
        override val errorCode = "PERM_001"
        override val userMessage = "マイクの使用許可が必要です。設定から許可してください。"
    }
    
    object PhoneStatePermissionDenied : PermissionError() {
        override val errorCode = "PERM_002"
        override val userMessage = "電話状態へのアクセス許可が必要です。設定から許可してください。"
    }
    
    object StoragePermissionDenied : PermissionError() {
        override val errorCode = "PERM_003"
        override val userMessage = "ストレージへのアクセス許可が必要です。設定から許可してください。"
    }
    
    object NotificationPermissionDenied : PermissionError() {
        override val errorCode = "PERM_004"
        override val userMessage = "通知の許可が必要です。設定から許可してください。"
    }
}

/**
 * Recording-related errors
 */
sealed class RecordingError : CallRecorderError() {
    
    object AudioSourceNotAvailable : RecordingError() {
        override val errorCode = "REC_001"
        override val userMessage = "音声ソースが利用できません。他のアプリが音声を使用している可能性があります。"
    }
    
    object InsufficientStorage : RecordingError() {
        override val errorCode = "REC_002"
        override val userMessage = "ストレージ容量が不足しています。空き容量を確保してください。"
    }
    
    object AudioProcessingFailed : RecordingError() {
        override val errorCode = "REC_003"
        override val userMessage = "音声処理に失敗しました。録音を再試行してください。"
    }
    
    object FileCreationFailed : RecordingError() {
        override val errorCode = "REC_004"
        override val userMessage = "録音ファイルの作成に失敗しました。"
    }
    
    object RecordingInProgress : RecordingError() {
        override val errorCode = "REC_005"
        override val userMessage = "既に録音が進行中です。"
    }
    
    object NoActiveRecording : RecordingError() {
        override val errorCode = "REC_006"
        override val userMessage = "アクティブな録音がありません。"
    }
    
    object RecordingTimeout : RecordingError() {
        override val errorCode = "REC_007"
        override val userMessage = "録音がタイムアウトしました。"
    }
}

/**
 * File system and storage errors
 */
sealed class StorageError : CallRecorderError() {
    
    object FileNotFound : StorageError() {
        override val errorCode = "STOR_001"
        override val userMessage = "ファイルが見つかりません。"
    }
    
    object FileCorrupted : StorageError() {
        override val errorCode = "STOR_002"
        override val userMessage = "ファイルが破損しています。"
    }
    
    object DirectoryCreationFailed : StorageError() {
        override val errorCode = "STOR_003"
        override val userMessage = "ディレクトリの作成に失敗しました。"
    }
    
    object FileDeletionFailed : StorageError() {
        override val errorCode = "STOR_004"
        override val userMessage = "ファイルの削除に失敗しました。"
    }
    
    object FileAccessDenied : StorageError() {
        override val errorCode = "STOR_005"
        override val userMessage = "ファイルへのアクセスが拒否されました。"
    }
    
    object StorageUnavailable : StorageError() {
        override val errorCode = "STOR_006"
        override val userMessage = "ストレージが利用できません。"
    }
}

/**
 * Database-related errors
 */
sealed class DatabaseError : CallRecorderError() {
    
    object DatabaseConnectionFailed : DatabaseError() {
        override val errorCode = "DB_001"
        override val userMessage = "データベースへの接続に失敗しました。"
    }
    
    object RecordNotFound : DatabaseError() {
        override val errorCode = "DB_002"
        override val userMessage = "レコードが見つかりません。"
    }
    
    object DatabaseCorrupted : DatabaseError() {
        override val errorCode = "DB_003"
        override val userMessage = "データベースが破損しています。"
    }
    
    object InsertFailed : DatabaseError() {
        override val errorCode = "DB_004"
        override val userMessage = "データの保存に失敗しました。"
    }
    
    object UpdateFailed : DatabaseError() {
        override val errorCode = "DB_005"
        override val userMessage = "データの更新に失敗しました。"
    }
    
    object DeleteFailed : DatabaseError() {
        override val errorCode = "DB_006"
        override val userMessage = "データの削除に失敗しました。"
    }
}

/**
 * Network and connectivity errors
 */
sealed class NetworkError : CallRecorderError() {
    
    object NoConnection : NetworkError() {
        override val errorCode = "NET_001"
        override val userMessage = "インターネット接続がありません。"
    }
    
    object ConnectionTimeout : NetworkError() {
        override val errorCode = "NET_002"
        override val userMessage = "接続がタイムアウトしました。"
    }
    
    object ServerError : NetworkError() {
        override val errorCode = "NET_003"
        override val userMessage = "サーバーエラーが発生しました。"
    }
}

/**
 * System and hardware errors
 */
sealed class SystemError : CallRecorderError() {
    
    object HardwareNotSupported : SystemError() {
        override val errorCode = "SYS_001"
        override val userMessage = "このデバイスではサポートされていない機能です。"
    }
    
    object LowMemory : SystemError() {
        override val errorCode = "SYS_002"
        override val userMessage = "メモリが不足しています。"
    }
    
    object ServiceUnavailable : SystemError() {
        override val errorCode = "SYS_003"
        override val userMessage = "サービスが利用できません。"
    }
    
    object UnknownError : SystemError() {
        override val errorCode = "SYS_999"
        override val userMessage = "予期しないエラーが発生しました。"
    }
}

/**
 * Validation errors
 */
sealed class ValidationError : CallRecorderError() {
    
    data class InvalidInput(val field: String) : ValidationError() {
        override val errorCode = "VAL_001"
        override val userMessage = "入力値が無効です: $field"
    }
    
    data class RequiredFieldMissing(val field: String) : ValidationError() {
        override val errorCode = "VAL_002"
        override val userMessage = "必須項目が入力されていません: $field"
    }
    
    data class InvalidFormat(val field: String, val expectedFormat: String) : ValidationError() {
        override val errorCode = "VAL_003"
        override val userMessage = "$field の形式が正しくありません。期待される形式: $expectedFormat"
    }
}