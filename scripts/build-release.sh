#!/bin/bash

# Pixel Call Recorder - リリースビルドスクリプト
# このスクリプトはリリース用APKを生成します

set -e

echo "🚀 Pixel Call Recorder リリースビルド開始"

# プロジェクトルートディレクトリに移動
cd "$(dirname "$0")/.."

# 環境変数の確認
if [ -z "$ANDROID_HOME" ]; then
    echo "❌ ANDROID_HOME環境変数が設定されていません"
    exit 1
fi

echo "✅ Android SDK: $ANDROID_HOME"

# Gradleラッパーの実行権限確認
if [ ! -x "./gradlew" ]; then
    chmod +x ./gradlew
fi

# クリーンビルド
echo "🧹 プロジェクトをクリーンアップ中..."
./gradlew clean

# リント チェック
echo "🔍 コード品質チェック中..."
./gradlew app:lintRelease

# テスト実行
echo "🧪 ユニットテスト実行中..."
./gradlew app:testReleaseUnitTest

# リリースビルド
echo "📦 リリースAPK生成中..."
./gradlew app:assembleRelease

# APKファイルの確認
APK_PATH="app/build/outputs/apk/release"
if [ -d "$APK_PATH" ]; then
    echo "✅ リリースAPKが生成されました:"
    ls -la "$APK_PATH"/*.apk
    
    # APKサイズの表示
    for apk in "$APK_PATH"/*.apk; do
        if [ -f "$apk" ]; then
            size=$(du -h "$apk" | cut -f1)
            echo "📏 APKサイズ: $size - $(basename "$apk")"
        fi
    done
else
    echo "❌ APKファイルが見つかりません"
    exit 1
fi

# Bundle生成（Google Play用）
echo "📱 Android App Bundle生成中..."
./gradlew app:bundleRelease

# Bundleファイルの確認
BUNDLE_PATH="app/build/outputs/bundle/release"
if [ -d "$BUNDLE_PATH" ]; then
    echo "✅ Android App Bundleが生成されました:"
    ls -la "$BUNDLE_PATH"/*.aab
    
    # Bundleサイズの表示
    for bundle in "$BUNDLE_PATH"/*.aab; do
        if [ -f "$bundle" ]; then
            size=$(du -h "$bundle" | cut -f1)
            echo "📏 Bundleサイズ: $size - $(basename "$bundle")"
        fi
    done
else
    echo "❌ Android App Bundleが見つかりません"
    exit 1
fi

# ビルド情報の出力
echo "📋 ビルド情報:"
echo "  日時: $(date)"
echo "  Git コミット: $(git rev-parse --short HEAD 2>/dev/null || echo 'N/A')"
echo "  ブランチ: $(git branch --show-current 2>/dev/null || echo 'N/A')"

# 成功メッセージ
echo "🎉 リリースビルドが完了しました！"
echo ""
echo "📁 出力ファイル:"
echo "  APK: $APK_PATH/"
echo "  Bundle: $BUNDLE_PATH/"
echo ""
echo "📝 次のステップ:"
echo "  1. APKファイルの動作確認"
echo "  2. 署名の確認"
echo "  3. Google Play Consoleへのアップロード"