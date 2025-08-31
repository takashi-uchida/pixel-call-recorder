#!/bin/bash

# Pixel Call Recorder - リリース検証スクリプト
# リリース前の最終検証を行います

set -e

echo "🔍 Pixel Call Recorder リリース検証開始"

# プロジェクトルートディレクトリに移動
cd "$(dirname "$0")/.."

# 色付きメッセージ用の関数
print_success() {
    echo "✅ $1"
}

print_warning() {
    echo "⚠️  $1"
}

print_error() {
    echo "❌ $1"
}

print_info() {
    echo "ℹ️  $1"
}

# 検証結果カウンター
PASSED=0
FAILED=0
WARNINGS=0

# 検証関数
validate_item() {
    local description="$1"
    local command="$2"
    
    echo -n "検証中: $description ... "
    
    if eval "$command" >/dev/null 2>&1; then
        print_success "OK"
        ((PASSED++))
    else
        print_error "FAILED"
        ((FAILED++))
    fi
}

validate_warning() {
    local description="$1"
    local command="$2"
    
    echo -n "確認中: $description ... "
    
    if eval "$command" >/dev/null 2>&1; then
        print_success "OK"
        ((PASSED++))
    else
        print_warning "WARNING"
        ((WARNINGS++))
    fi
}

echo "📋 基本設定の検証"
echo "===================="

# Gradle設定の確認
validate_item "Gradle Wrapper存在確認" "[ -f ./gradlew ]"
validate_item "build.gradle存在確認" "[ -f app/build.gradle ]"
validate_item "ProGuard設定確認" "[ -f app/proguard-rules.pro ]"

# マニフェスト確認
validate_item "AndroidManifest.xml存在確認" "[ -f app/src/main/AndroidManifest.xml ]"

# リソース確認
validate_item "アプリアイコン確認" "[ -f app/src/main/res/mipmap-hdpi/ic_launcher.xml ]"
validate_item "文字列リソース確認" "[ -f app/src/main/res/values/strings.xml ]"

echo ""
echo "🏗️  ビルド設定の検証"
echo "===================="

# ビルド設定確認
validate_item "リリースビルド設定確認" "grep -q 'minifyEnabled true' app/build.gradle"
validate_item "ProGuard設定適用確認" "grep -q 'proguard-rules.pro' app/build.gradle"
validate_item "リソース圧縮設定確認" "grep -q 'shrinkResources true' app/build.gradle"

echo ""
echo "📱 アプリケーション構成の検証"
echo "=========================="

# 主要クラスの存在確認
validate_item "MainActivity存在確認" "[ -f app/src/main/java/com/callrecorder/pixel/ui/MainActivity.kt ]"
validate_item "CallRecordingService存在確認" "[ -f app/src/main/java/com/callrecorder/pixel/service/CallRecordingServiceImpl.kt ]"
validate_item "AudioProcessor存在確認" "[ -f app/src/main/java/com/callrecorder/pixel/audio/AudioProcessor.kt ]"
validate_item "PermissionManager存在確認" "[ -f app/src/main/java/com/callrecorder/pixel/permission/PermissionManager.kt ]"

echo ""
echo "🧪 テストファイルの検証"
echo "===================="

# テストファイル確認
validate_warning "ユニットテスト存在確認" "find app/src/test -name '*.kt' | wc -l | grep -v '^0$'"
validate_warning "統合テスト存在確認" "find app/src/test -path '*/integration/*' -name '*.kt' | wc -l | grep -v '^0$'"

echo ""
echo "📄 ドキュメントの検証"
echo "=================="

# ドキュメント確認
validate_warning "README存在確認" "[ -f README.md ]"
validate_item "リリースチェックリスト確認" "[ -f RELEASE_CHECKLIST.md ]"
validate_warning "プライバシーポリシー確認" "grep -q 'privacy' app/src/main/res/values/strings.xml"

echo ""
echo "🔒 セキュリティ設定の検証"
echo "======================"

# セキュリティ設定確認
validate_item "権限宣言確認" "grep -q 'RECORD_AUDIO' app/src/main/AndroidManifest.xml"
validate_item "サービス宣言確認" "grep -q 'CallRecordingService' app/src/main/AndroidManifest.xml"
validate_item "レシーバー宣言確認" "grep -q 'PhoneStateReceiver' app/src/main/AndroidManifest.xml"

echo ""
echo "📊 コード品質の検証"
echo "=================="

# コード品質確認
validate_warning "Kotlinファイル数確認" "find app/src/main -name '*.kt' | wc -l | grep -v '^0$'"
validate_warning "リソースファイル確認" "find app/src/main/res -name '*.xml' | wc -l | grep -v '^0$'"

echo ""
echo "🎯 リリース固有設定の検証"
echo "======================"

# リリース設定確認
validate_item "バージョン設定確認" "grep -q 'versionName' app/build.gradle"
validate_item "アプリケーションID確認" "grep -q 'applicationId' app/build.gradle"
validate_warning "署名設定準備確認" "[ -f app/release-config.gradle ]"

echo ""
echo "📈 検証結果サマリー"
echo "=================="

TOTAL=$((PASSED + FAILED + WARNINGS))

print_info "総検証項目数: $TOTAL"
print_success "成功: $PASSED"
print_warning "警告: $WARNINGS"
print_error "失敗: $FAILED"

echo ""

if [ $FAILED -eq 0 ]; then
    if [ $WARNINGS -eq 0 ]; then
        print_success "🎉 全ての検証項目をクリアしました！リリース準備完了です。"
        echo ""
        echo "📝 次のステップ:"
        echo "  1. ./scripts/build-release.sh を実行してリリースビルドを作成"
        echo "  2. 実機での最終動作確認"
        echo "  3. Google Play Consoleへのアップロード"
        exit 0
    else
        print_warning "⚠️  警告項目があります。確認してからリリースしてください。"
        echo ""
        echo "📝 推奨アクション:"
        echo "  1. 警告項目の確認と対応"
        echo "  2. 必要に応じて追加のテストやドキュメント作成"
        echo "  3. 問題なければリリースビルドの実行"
        exit 0
    fi
else
    print_error "❌ 検証に失敗した項目があります。修正してから再実行してください。"
    echo ""
    echo "📝 必要なアクション:"
    echo "  1. 失敗項目の修正"
    echo "  2. 検証スクリプトの再実行"
    echo "  3. 全項目クリア後にリリースビルド実行"
    exit 1
fi