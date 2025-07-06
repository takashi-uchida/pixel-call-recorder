# Apple Silicon ネイティブ ARM64 ベースイメージ
FROM mcr.microsoft.com/vscode/devcontainers/base:ubuntu-24.04

ARG ANDROID_SDK_VERSION=35
ARG ANDROID_NDK_VERSION=27.2.12479018

# 必要ツールを ARM64 でインストール
RUN apt-get update && \
    apt-get install -y \
        wget \
        unzip \
        openjdk-17-jdk-headless \
        build-essential \
        git \
        curl && \
    rm -rf /var/lib/apt/lists/*

# Android 環境変数設定
ENV ANDROID_HOME=/home/vscode/Android/Sdk
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator

# vscode ユーザーでの作業に切り替え
USER vscode
WORKDIR /home/vscode

# Android SDK ディレクトリ準備
RUN mkdir -p $ANDROID_HOME/cmdline-tools

# 最新の command-line tools を ARM64 で取得
RUN cd /tmp && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip && \
    unzip -q commandlinetools-linux-*_latest.zip && \
    mv cmdline-tools $ANDROID_HOME/cmdline-tools/latest && \
    rm commandlinetools-linux-*_latest.zip

# ライセンス同意とSDKコンポーネントのインストール
# 複数回のライセンス同意で確実に処理
RUN yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses || true && \
    $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
        "platform-tools" \
        "platforms;android-${ANDROID_SDK_VERSION}" \
        "build-tools;34.0.0" \
        "ndk;${ANDROID_NDK_VERSION}" \
        "system-images;android-${ANDROID_SDK_VERSION};google_apis;arm64-v8a" || true

# Gradle インストール（最新版）
RUN cd /tmp && \
    wget -q https://services.gradle.org/distributions/gradle-8.11.1-bin.zip && \
    unzip -q gradle-8.11.1-bin.zip && \
    sudo mv gradle-8.11.1 /opt/gradle && \
    sudo ln -s /opt/gradle/bin/gradle /usr/local/bin/gradle && \
    rm gradle-8.11.1-bin.zip

# 環境変数を bashrc と profile に追加
RUN echo "export ANDROID_HOME=$ANDROID_HOME" >> ~/.bashrc && \
    echo "export PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/emulator" >> ~/.bashrc && \
    echo "export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64" >> ~/.bashrc && \
    echo "export ANDROID_HOME=$ANDROID_HOME" >> ~/.profile && \
    echo "export PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/emulator" >> ~/.profile && \
    echo "export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64" >> ~/.profile

# 作業ディレクトリ設定
WORKDIR /workspaces/pixel-call-recorder

# ARM64 最適化完了メッセージ
RUN echo "ARM64 Android devcontainer setup complete!"