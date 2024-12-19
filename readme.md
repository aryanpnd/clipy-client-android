# Clipy Android

Clipy is a clipboard synchronization app that allows you to sync clipboard data between your Android device and a server. It runs as a background service that listens for clipboard changes, and you can also share text via other apps (e.g., browser, email) directly to the server without opening the app.

## Features
- Clipboard synchronization between Android and server via WebSocket.
- Share text from any app and send it to the server.
- Notifications to manage service status (pause, resume, stop).
- Works as a background service even when the app isn't open.

## Installation

To set up the Android part of the project:

### Prerequisites
1. Android Studio installed.
2. A server running WebSocket (currently set to `ws://192.168.255.229:8080`).
3. Android device with **Android 8.0 (API level 26)** or higher.

### Steps
1. Clone the repository:

    ```bash
    git clone https://github.com/aryanpnd/clipy-client-android
    cd clipy
    ```

2. Open the project in Android Studio.

3. Build and run the app on your Android device.

4. Make sure the WebSocket server is running and accessible.

## How It Works

### Clipboard Synchronization
- **ClipboardService**: A foreground service that manages clipboard synchronization over a WebSocket connection. The service listens for messages from the server and sends clipboard content to the server when updated.
- **WebSocket**: The service maintains a persistent connection to the server, ensuring that clipboard data is synced seamlessly. If the connection is lost, it automatically attempts to reconnect.

### Text Sharing
You can share text from any app to the server directly. Once text is shared:
- If the clipboard service is running, it will send the text to the server over WebSocket.
- If the service is not running, a toast message will notify the user.

### Service Actions
The ClipboardService can be controlled via the following actions:
- **Pause**: Pauses clipboard synchronization.
- **Resume**: Resumes clipboard synchronization.
- **Stop**: Stops the service entirely.

### Notifications
- The app displays a notification to inform users of the clipboard sync status.
- You can pause, resume, or stop the service from the notification itself.

## Manifest Permissions

The app requires the following permissions:

- **INTERNET**: To communicate with the WebSocket server.
- **FOREGROUND_SERVICE**: To run the clipboard synchronization service in the background.
- **FOREGROUND_SERVICE_DATA_SYNC**: To handle data sync operations in the background.
- **SYSTEM_ALERT_WINDOW**: For special access (if needed).
- **POST_NOTIFICATIONS**: To send notifications for service status.

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### ClipboardService in the Manifest
```xml
<service
    android:name=".ClipboardService"
    android:exported="false"
    android:foregroundServiceType="dataSync" />
```

### MainActivity in the Manifest
```xml
<activity
    android:name=".MainActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

## How to Share Text
When you want to share text to the server:
1. **Copy** the text you want to send.
2. Open the app that supports text sharing (e.g., Browser, Messaging App).
3. Select **Share** and choose **Clipy** as the app.
4. If the ClipboardService is running, the text will be sent directly to the server.
5. If the service is not running, a toast will appear with an error message.

## Screenshots
(Include any relevant screenshots of the app, service, or notifications here)

## Future Improvements
- Add support for clipboard sync across multiple devices (e.g., desktop app).
- Improve error handling and UI/UX for better user experience.

## Contributing

Contributions are welcome! Feel free to open issues or submit pull requests to improve the project. Please follow the guidelines below for submitting contributions:
1. Fork the repository.
2. Create a new branch (`git checkout -b feature-name`).
3. Commit your changes (`git commit -am 'Add new feature'`).
4. Push to the branch (`git push origin feature-name`).
5. Open a pull request.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
