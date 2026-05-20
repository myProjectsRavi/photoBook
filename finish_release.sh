#!/bin/bash

echo "🚀 Starting PhotoBook Release Process..."
cd "/Users/ravitejanekkalapu/Documents/Code Repos/Photo Book/photoBook"

echo "📦 1. Pushing to GitHub..."
git push origin main
if [ $? -ne 0 ]; then
    echo "❌ Git push failed. Please check your network or authentication."
    exit 1
fi
echo "✅ Pushed successfully."

echo "🔨 2. Building Release AAB..."
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew clean :app:bundleRelease
if [ $? -ne 0 ]; then
    echo "❌ Build failed. Please check the errors above."
    exit 1
fi

echo "📋 Copying AAB to outputs directory..."
mkdir -p outputs/bundle
cp app/build/outputs/bundle/release/app-release.aab outputs/bundle/PhotoBook-v2.0.0-glass-ui-vc7.aab
echo "✅ Build complete. AAB is at: outputs/bundle/PhotoBook-v2.0.0-glass-ui-vc7.aab"

echo "📂 3. Opening Play Store Assets folder..."
# Create a local folder and copy the assets from the AI's internal brain folder so they are easy to drag and drop
mkdir -p outputs/playstore-assets
cp /Users/ravitejanekkalapu/.gemini/antigravity/brain/d5d07f36-2210-4867-a70d-150dfd8e6d40/*.png outputs/playstore-assets/ 2>/dev/null
open outputs/playstore-assets

echo "🌐 4. Opening Play Console in your browser..."
open "https://play.google.com/console/u/0/developers/7517725463519393699/app/4975631467518652850/store-listing"

echo ""
echo "🎉 Almost there! Please complete the final manual steps:"
echo "1. Drag and drop the screenshots from the opened folder into the Play Console."
echo "2. Upload the AAB file from photoBook/outputs/bundle/ to a new Production release."
echo "3. Submit for review!"
