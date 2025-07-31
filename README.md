# Canvas-One-Studio AAR Integration Guide

## Overview
This document provides comprehensive instructions for integrating the Canvas-One-Studio AAR file into client applications. The AAR contains advanced video interruption functionality with persistent form tracking and dynamic API integration.

## 📦 AAR Package Contents

### **AAR File**
- **File Name**: `AELLayer-release.aar`
- **Location**: `AAR/AELLayer-release.aar`
- **Purpose**: Contains compiled video interruption library with all dependencies

### **Integration Files**
The following files need to be updated/merged in the client application:

#### **1. VideoInterruptionIntegration.kt**
- **Source Location**: `Modified_files/VideoInterruptionIntegration.kt`
- **Client Target**: `app/src/main/java/com/[client-package]/integration/VideoInterruptionIntegration.kt`
- **Purpose**: High-level integration wrapper for seamless ExoPlayer integration

#### **2. PlayerActivity.kt**
- **Source Location**: `Modified_files/PlayerActivity.kt`
- **Client Target**: `app/src/main/java/com/[client-package]/ui/PlayerActivity.kt`
- **Purpose**: Video ID consumption and AAR initialization

---

## 🚀 Deployment Instructions

### **Step 1: Deploy AAR File**

#### **1.1 Copy AAR to Client Project**
```bash
# Copy AAR file to client's libs folder
cp AAR/AELLayer-release.aar [CLIENT_PROJECT]/app/libs/
```

#### **1.2 Update Client's build.gradle**
Add the following dependency to the client's `app/build.gradle`:

```gradle
dependencies {
    // Canvas-One-Studio Video Interruption Library
    implementation files('AAR/AELLayer-release.aar')
    
    // Required dependencies (if not already present)
    implementation 'androidx.media3:media3-exoplayer:1.7.1'
    implementation 'androidx.media3:media3-ui:1.7.1'
    implementation 'androidx.media3:media3-common:1.7.1'
    implementation 'com.squareup.okhttp3:okhttp:3.14.9'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4'
}
```

### **Step 2: Update Integration Files**

#### **2.1 VideoInterruptionIntegration.kt**

**Purpose**: Merge the integration wrapper code into client's project

**Action Required**:
- Copy the complete `VideoInterruptionIntegration.kt` file to client's integration package
- Update package name to match client's structure
- Ensure proper imports are maintained

**Key Features**:
- Simplified ExoPlayer integration
- Automatic lifecycle management
- Position monitoring with optimized intervals
- Callback interface for analytics integration
