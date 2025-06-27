#!/bin/bash

# Install Android Command Line Tools
echo "Installing Android SDK..."
wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip -O /tmp/cmdline-tools.zip
mkdir -p /opt/android-sdk/cmdline-tools/latest
unzip -q /tmp/cmdline-tools.zip -d /opt/android-sdk/cmdline-tools/latest
mv /opt/android-sdk/cmdline-tools/latest/cmdline-tools/* /opt/android-sdk/cmdline-tools/latest/
rm -rf /opt/android-sdk/cmdline-tools/latest/cmdline-tools
rm /tmp/cmdline-tools.zip

# Set environment variables
echo "Setting up environment variables..."
echo "export ANDROID_HOME=/opt/android-sdk" >> ~/.bashrc
echo "export PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools" >> ~/.bashrc
source ~/.bashrc

# Install required Android components
echo "Installing Android SDK components..."
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" "emulator"

# Verify installation
echo "Android SDK installation complete"
echo "ANDROID_HOME: $ANDROID_HOME"
echo "Java version:"
java -version