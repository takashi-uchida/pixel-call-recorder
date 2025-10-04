# Codex CLI GitHub Workflow セットアップガイド

## 概要

このガイドでは、Codex CLIをGitHub Workflowで使用できるようにする方法を説明します。

## 制限事項

GitHub Appの権限により、`.github/workflows/`ディレクトリ内のファイルを直接変更することができません。そのため、以下の手順で手動で設定を行う必要があります。

## 推奨される変更内容

現在の `.github/workflows/codex.yml` ファイルに以下の変更を加えることをお勧めします：

### 変更点

1. **workflow_dispatch トリガーの追加**
   - GitHub UIから手動でワークフローを実行できるようになります
   - カスタムコマンドを指定して実行可能

2. **リポジトリチェックアウトステップの追加**
   - ワークフロー内でリポジトリのコードにアクセス可能

3. **既存機能の維持**
   - issue_comment トリガー（`@codex` でのコメント実行）
   - 既存の設定変数

### 更新後のファイル内容

```yaml
# .github/workflows/codex.yml - Codex CLI workflow
name: Codex CLI

on:
  issue_comment:
    types: [created]
  workflow_dispatch:
    inputs:
      command:
        description: 'Codex CLI command to run'
        required: true
        type: string

jobs:
  resolve:
    permissions:
      contents: read
      issues: write
      pull-requests: write
    runs-on: ubuntu-latest
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Run Codex action
        uses: takashi-uchida/codex-github-actions@main
        with:
          trigger_prefix: ${{ vars.CODEX_TRIGGER || '@codex' }}
          model:          ${{ vars.LLM_MODEL     || 'o4-mini' }}
          mention_author: ${{ vars.MENTION_AUTHOR || true }}
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
```

## 適用方法

### 方法1: GitHubのWeb UIで編集

1. GitHubリポジトリで `.github/workflows/codex.yml` に移動
2. 「Edit this file」ボタンをクリック
3. 上記の内容に置き換え
4. コミットメッセージ: `feat: Codex CLIをGitHub Workflowで使えるように改善`
5. 「Commit changes」をクリック

### 方法2: ローカルで編集

```bash
# リポジトリをクローン
git clone https://github.com/takashi-uchida/pixel-call-recorder.git
cd pixel-call-recorder

# ファイルを編集
nano .github/workflows/codex.yml

# 変更をコミット
git add .github/workflows/codex.yml
git commit -m "feat: Codex CLIをGitHub Workflowで使えるように改善"
git push origin main
```

## 使用方法

### 1. Issue コメントから実行（既存機能）

Issueまたはプルリクエストで `@codex` とメンションすることで実行されます。

### 2. GitHub UIから手動実行（新機能）

1. リポジトリの「Actions」タブに移動
2. 「Codex CLI」ワークフローを選択
3. 「Run workflow」ボタンをクリック
4. コマンドを入力して実行

## 利点

- **柔軟性**: UIから直接コマンドを実行可能
- **デバッグ**: 手動実行によりテストが容易
- **統合性**: 既存のissue commentトリガーも維持
- **コードアクセス**: リポジトリのコードに直接アクセス可能

## トラブルシューティング

ワークフローが動作しない場合：

1. `OPENAI_API_KEY` シークレットが設定されているか確認
2. リポジトリの Settings > Actions で必要な権限が有効か確認
3. ワークフローのログを確認してエラーメッセージを確認

---

Generated with [Claude Code](https://claude.ai/code)
