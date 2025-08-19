# expo-sms-manager

A comprehensive SMS management module for React Native/Expo that enables sending, receiving, and managing SMS messages directly from your app without opening the default SMS application.

## Features

- ✅ **Send SMS directly** - No need to open the SMS app
- ✅ **Receive SMS** - Listen for incoming messages in real-time
- ✅ **Dual SIM support** - Select which SIM to use for sending
- ✅ **Signal strength checking** - Verify connectivity before sending
- ✅ **Delivery tracking** - Get sent and delivered confirmations
- ✅ **Bulk sending** - Send to multiple recipients
- ✅ **Long message support** - Automatic multipart SMS handling
- ✅ **OTP extraction** - Built-in utilities for OTP detection
- ✅ **Search capabilities** - Find messages by sender or content

## Platform Support

**Android**: Full support (API 21+)

## Installation

### For Managed Expo Projects

```bash
npx expo install expo-sms-manager
```

### For Bare React Native Projects

```bash
npm install expo-sms-manager
```

Then run:

```bash
npx expo prebuild
```

## Permissions

Add these permissions to your `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.SEND_SMS" />
<uses-permission android:name="android.permission.READ_SMS" />
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.READ_PHONE_NUMBERS" />
```

## Quick Start

```javascript
import * as SmsManager from 'expo-sms-manager';
import { PermissionsAndroid, Platform } from 'react-native';

// Request permissions (Android only)
async function requestSmsPermissions() {
  if (Platform.OS !== 'android') return false;
  
  const granted = await PermissionsAndroid.requestMultiple([
    PermissionsAndroid.PERMISSIONS.SEND_SMS,
    PermissionsAndroid.PERMISSIONS.READ_SMS,
    PermissionsAndroid.PERMISSIONS.RECEIVE_SMS,
    PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE,
  ]);
  
  return Object.values(granted).every(
    status => status === PermissionsAndroid.RESULTS.GRANTED
  );
}

// Send an SMS
async function sendMessage() {
  try {
    const result = await SmsManager.sendSms(
      '+1234567890', 
      'Hello from my app!',
      {
        simSlot: 0, // Use first SIM
        requestStatusReport: false, // Don't wait for delivery
        checkSignal: true, // Check signal before sending
      }
    );
    console.log('SMS sent:', result.messageId);
  } catch (error) {
    console.error('Failed to send SMS:', error);
  }
}

// Listen for incoming SMS
SmsManager.addSmsListener((message) => {
  console.log('New SMS from:', message.sender);
  console.log('Message:', message.message);
  
  // Check for OTP
  const otp = SmsManager.extractOtp(message.message);
  if (otp) {
    console.log('OTP detected:', otp);
  }
});

// Start listening for SMS
await SmsManager.startSmsListener();
```

## API Reference

### Core Functions

#### `isSupported()`

Check if SMS operations are supported on the current platform.

```javascript
const supported = SmsManager.isSupported(); // true on Android
```

#### `hasPermissions()`

Check if all required SMS permissions are granted.

```javascript
const hasPerms = SmsManager.hasPermissions();
```

### Sending SMS

#### `sendSms(phoneNumber, message, options?)`

Send an SMS message directly without opening the SMS app.

```javascript
const result = await SmsManager.sendSms('+1234567890', 'Hello!', {
  simSlot: 0,              // SIM slot index (0 or 1)
  requestStatusReport: false, // Request delivery confirmation
  checkSignal: true,       // Check signal before sending
});
```

**Note**: Set `requestStatusReport: false` for immediate response. When set to `true`, the promise may take longer to resolve as it waits for network confirmation.

#### `sendSmsToMultiple(phoneNumbers, message, options?)`

Send SMS to multiple recipients.

```javascript
const results = await SmsManager.sendSmsToMultiple(
  ['+1234567890', '+0987654321'],
  'Bulk message',
  { simSlot: 0 }
);
```

#### `sendLongSms(phoneNumber, message, options?)`

Send a long SMS that will be automatically split into multiple parts.

```javascript
const result = await SmsManager.sendLongSms(
  '+1234567890',
  'Very long message that exceeds 160 characters...',
  { simSlot: 0 }
);
```

### SIM Card Management

#### `getAvailableSimCards()`

Get information about available SIM cards.

```javascript
const simCards = SmsManager.getAvailableSimCards();
// Returns: [{ slot: 0, displayName: "SIM 1", carrierName: "Carrier", ... }]
```

#### `checkSignalStrength(simSlot)`

Check signal strength for a specific SIM slot.

```javascript
const signal = await SmsManager.checkSignalStrength(0);
// Returns: { signalStrength: 3, signalLevel: "good", hasSignal: true }
```

### Receiving SMS

#### `startSmsListener()`

Start listening for incoming SMS messages.

```javascript
await SmsManager.startSmsListener();
```

#### `stopSmsListener()`

Stop listening for incoming SMS messages.

```javascript
await SmsManager.stopSmsListener();
```

#### `addSmsListener(callback)`

Add a listener for incoming SMS messages.

```javascript
const subscription = SmsManager.addSmsListener((message) => {
  console.log('New SMS:', message);
});

// Later: remove the listener
subscription.remove();
```

### Reading SMS

#### `getRecentSms(limit)`

Get recent SMS messages from the device.

```javascript
const messages = await SmsManager.getRecentSms(10);
```

#### `getSmsFromNumber(phoneNumber, limit)`

Get SMS messages from a specific phone number.

```javascript
const messages = await SmsManager.getSmsFromNumber('+1234567890', 10);
```

#### `findSmsWithText(searchText, limit)`

Search for SMS messages containing specific text.

```javascript
const messages = await SmsManager.findSmsWithText('OTP', 5);
```

### Event Listeners

#### SMS Events

```javascript
// SMS received
SmsManager.addSmsListener((message) => {
  console.log('Received:', message);
});

// SMS send progress
SmsManager.addSmsProgressListener((progress) => {
  console.log('Progress:', progress.status);
});

// SMS sent confirmation
SmsManager.addSmsSentListener((event) => {
  console.log('Sent:', event.status);
});

// SMS delivered confirmation
SmsManager.addSmsDeliveredListener((event) => {
  console.log('Delivered:', event.status);
});

// Error events
SmsManager.addErrorListener((error) => {
  console.error('Error:', error.message);
});
```

### Utility Functions

#### `extractOtp(message, length?)`

Extract OTP code from a message.

```javascript
const otp = SmsManager.extractOtp('Your OTP is 123456');
// Returns: "123456"
```

#### `isValidPhoneNumber(phoneNumber)`

Validate phone number format.

```javascript
const isValid = SmsManager.isValidPhoneNumber('+1234567890');
```

#### `formatPhoneNumber(phoneNumber, countryCode?)`

Format a phone number.

```javascript
const formatted = SmsManager.formatPhoneNumber('1234567890', '+1');
// Returns: "+11234567890"
```

## Types

```typescript
interface SmsMessage {
  id?: number;
  sender: string;
  message: string;
  timestamp: number;
  date: string;
  type?: 'received' | 'sent';
}

interface SmsSendOptions {
  simSlot?: number;           // 0 or 1
  requestStatusReport?: boolean;
  checkSignal?: boolean;
  waitForDelivery?: boolean;  // Wait for delivery confirmation
}

interface SmsSendResult {
  messageId: string;
  sent: 'sent' | 'failed' | 'pending';
  delivered?: 'delivered' | 'pending' | 'timeout';
}

interface SimCardInfo {
  slot: number;
  displayName: string;
  carrierName?: string;
  isActive: boolean;
}
```

## Troubleshooting

### Loading Spinner Stuck After Sending SMS

If the loading spinner remains after sending an SMS, ensure you're using `requestStatusReport: false` for immediate response:

```javascript
const result = await SmsManager.sendSms(phoneNumber, message, {
  requestStatusReport: false, // Immediate response
});
```

Delivery reports may not always arrive, especially on physical devices.

### Permissions Issues

Always request permissions at runtime on Android:

```javascript
const granted = await PermissionsAndroid.requestMultiple([
  PermissionsAndroid.PERMISSIONS.SEND_SMS,
  PermissionsAndroid.PERMISSIONS.READ_SMS,
  PermissionsAndroid.PERMISSIONS.RECEIVE_SMS,
  PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE,
]);
```

### Google Play Store Submission

Apps using SMS permissions may require additional justification during Play Store review. Ensure your app's use case complies with [Google&#39;s SMS and Call Log Permissions policy](https://support.google.com/googleplay/android-developer/answer/9047303).

## Example App

See the [example folder](./example) for a complete implementation with UI components.

## Contributing

Contributions are welcome! Please read our [contributing guidelines](CONTRIBUTING.md) before submitting PRs.

## License

MIT

## Support

- 📖 [Documentation](https://github.com/cob-byte/expo-sms-manager)
- 🐛 [Issue Tracker](https://github.com/cob-byte/expo-sms-manager/issues)
- 💬 [Discussions](https://github.com/cob-byte/expo-sms-manager/discussions)

## Changelog

### Version 1.0.2

- Initial release with full SMS sending and receiving capabilities
- Dual SIM support
- Signal strength checking
- Delivery tracking
- OTP extraction utilities

---

Made with ❤️ for the React Native community
