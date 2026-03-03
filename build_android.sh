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

# 5. Build the Android project
echo "🔨 Building........."
briefcase build android

echo ""
echo "🎉 SUCCESS! Your Piggytrade build is complete."
echo "📍 Find your APK at: build/piggytrade/android/gradle/app/build/outputs/apk/debug/app-debug.apk"
