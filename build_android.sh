#!/bin/bash

set -e

echo "🚀 Starting Briefcase Android Build Process..."

# 1. Create virtual environment if it doesn't exist
if [ ! -d ".venv" ]; then
    echo "📦 Creating virtual environment (.venv)..."
    python3 -m venv .venv
else
    echo "✅ Virtual environment already exists."
fi

# 2. Activate virtual environment
echo "🔌 Activating virtual environment..."
source .venv/bin/activate

# 3. Install/Update requirements
echo "📥 Installing requirements (this includes Briefcase)..."
pip install --upgrade pip
pip install -r requirements.txt

# 4. Create or Update the Android project
echo "🏗️ Updating Android project and resources..."
briefcase update android --update-resources

echo "🔏 Injecting Biometric Dependencies..."
GRADLE_FILE="build/piggytrade/android/gradle/app/build.gradle"
if [ -f "$GRADLE_FILE" ]; then
    if grep -q "androidx.biometric" "$GRADLE_FILE"; then
        echo "   (Biometric library present)"
    else
        sed -i '/dependencies {/a \    implementation "androidx.biometric:biometric:1.1.0"' "$GRADLE_FILE"
        echo "   (Injected into build.gradle dependencies)"
    fi
    
    if grep -q "staticProxy" "$GRADLE_FILE"; then
        echo "   (StaticProxy configuration present)"
    else
        sed -i '/python {/a \        staticProxy "piggytrade.biometrics"' "$GRADLE_FILE"
        echo "   (Injected staticProxy into build.gradle)"
    fi
fi

MANIFEST_FILE="build/piggytrade/android/gradle/app/src/main/AndroidManifest.xml"
if [ -f "$MANIFEST_FILE" ]; then
    if grep -q "USE_BIOMETRIC" "$MANIFEST_FILE"; then
        echo "   (Permission exists in Manifest)"
    else
        sed -i '/<manifest/a \    <uses-permission android:name="android.permission.USE_BIOMETRIC" />' "$MANIFEST_FILE"
        echo "   (Injected into Manifest)"
    fi
fi

# 5. Build the Android project
echo "🔨 Building........."
briefcase build android

echo ""
echo "🎉 SUCCESS! Your Piggytrade build is complete."
echo "📍 Find your APK at: build/piggytrade/android/gradle/app/build/outputs/apk/debug/app-debug.apk"
