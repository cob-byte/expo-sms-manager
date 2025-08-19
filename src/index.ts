import { Platform } from 'react-native';
import { EventSubscription } from 'expo-modules-core';
import ExpoSmsManagerModule from './ExpoSmsManagerModule';
import { 
  SmsMessage, 
  SmsError, 
  SmsSendOptions,
  SmsSendResult,
  SmsMultipleResult,
  SimCardInfo,
  SignalStrengthInfo,
  SmsProgressEvent,
  SmsSentEvent,
  SmsDeliveredEvent,
  ExpoSmsManagerModuleEvents,
  DEFAULT_SMS_OPTIONS
} from './ExpoSmsManager.types';

// Export types
export type { 
  SmsMessage, 
  SmsError, 
  SmsSendOptions,
  SmsSendResult,
  SmsMultipleResult,
  SimCardInfo,
  SignalStrengthInfo,
  SmsProgressEvent,
  SmsSentEvent,
  SmsDeliveredEvent,
  ExpoSmsManagerModuleEvents 
};

/**
 * Checks if the current platform supports SMS operations
 * @returns true if platform is Android, false otherwise
 */
export function isSupported(): boolean {
  return Platform.OS === 'android';
}

/**
 * Checks if the app has all required SMS permissions (READ, RECEIVE, SEND)
 * @returns true if all SMS permissions are granted
 */
export function hasPermissions(): boolean {
  if (!isSupported()) {
    return false;
  }
  return ExpoSmsManagerModule.hasPermissions();
}

// ===== SENDING FUNCTIONS WITH FIXES =====

/**
 * Sends an SMS message directly without opening the SMS app
 * 
 * IMPORTANT: By default, this returns immediately after sending to the network
 * without waiting for delivery confirmation. Set requestStatusReport: true and
 * waitForDelivery: true if you need delivery confirmation, but be aware that
 * delivery reports may not always arrive, especially on physical devices.
 * 
 * @param phoneNumber The recipient's phone number
 * @param message The SMS message content
 * @param options Optional sending configuration
 * @returns Promise that resolves with send result including messageId and status
 */
export async function sendSms(
  phoneNumber: string, 
  message: string, 
  options?: SmsSendOptions
): Promise<SmsSendResult> {
  if (!isSupported()) {
    throw new Error('SMS operations are only supported on Android platform');
  }
  
  if (!phoneNumber || phoneNumber.trim() === '') {
    throw new Error('Phone number is required');
  }
  
  if (!message || message.trim() === '') {
    throw new Error('Message content is required');
  }
  
  // Merge with defaults, ensuring requestStatusReport defaults to false
  const finalOptions = {
    ...DEFAULT_SMS_OPTIONS,
    ...options
  };
  
  return await ExpoSmsManagerModule.sendSms(phoneNumber.trim(), message, finalOptions);
}

/**
 * Sends an SMS message with delivery tracking
 * This is a convenience method that enables delivery tracking but doesn't block
 * 
 * @param phoneNumber The recipient's phone number
 * @param message The SMS message content
 * @param options Optional sending configuration
 * @returns Promise that resolves immediately after sending, with delivery events sent separately
 */
export async function sendSmsWithTracking(
  phoneNumber: string,
  message: string,
  options?: Omit<SmsSendOptions, 'requestStatusReport' | 'waitForDelivery'>
): Promise<SmsSendResult> {
  return sendSms(phoneNumber, message, {
    ...options,
    requestStatusReport: true,
    waitForDelivery: false, // Don't block waiting for delivery
  });
}

/**
 * Sends an SMS message and waits for delivery confirmation
 * WARNING: This may take a long time or timeout on physical devices
 * 
 * @param phoneNumber The recipient's phone number
 * @param message The SMS message content
 * @param options Optional sending configuration
 * @returns Promise that resolves when delivery is confirmed or times out
 */
export async function sendSmsAndWaitForDelivery(
  phoneNumber: string,
  message: string,
  options?: Omit<SmsSendOptions, 'requestStatusReport' | 'waitForDelivery'>
): Promise<SmsSendResult> {
  return sendSms(phoneNumber, message, {
    ...options,
    requestStatusReport: true,
    waitForDelivery: true,
    deliveryTimeout: options?.deliveryTimeout || 30000, // 30 second default timeout
  });
}

/**
 * Sends an SMS message to multiple recipients
 * @param phoneNumbers Array of recipient phone numbers
 * @param message The SMS message content
 * @param options Optional sending configuration
 * @returns Promise that resolves with array of results for each number
 */
export async function sendSmsToMultiple(
  phoneNumbers: string[], 
  message: string, 
  options?: SmsSendOptions
): Promise<SmsMultipleResult[]> {
  if (!isSupported()) {
    throw new Error('SMS operations are only supported on Android platform');
  }
  
  if (!phoneNumbers || phoneNumbers.length === 0) {
    throw new Error('At least one phone number is required');
  }
  
  if (!message || message.trim() === '') {
    throw new Error('Message content is required');
  }
  
  const cleanNumbers = phoneNumbers
    .filter(num => num && num.trim() !== '')
    .map(num => num.trim());
  
  if (cleanNumbers.length === 0) {
    throw new Error('No valid phone numbers provided');
  }
  
  const finalOptions = {
    ...DEFAULT_SMS_OPTIONS,
    ...options
  };
  
  return await ExpoSmsManagerModule.sendSmsToMultiple(cleanNumbers, message, finalOptions);
}

/**
 * Sends a long SMS message (automatically splits into multiple parts)
 * @param phoneNumber The recipient's phone number
 * @param message The long SMS message content
 * @param options Optional sending configuration
 * @returns Promise that resolves with send result
 */
export async function sendLongSms(
  phoneNumber: string, 
  message: string, 
  options?: SmsSendOptions
): Promise<SmsSendResult> {
  if (!isSupported()) {
    throw new Error('SMS operations are only supported on Android platform');
  }
  
  if (!phoneNumber || phoneNumber.trim() === '') {
    throw new Error('Phone number is required');
  }
  
  if (!message || message.trim() === '') {
    throw new Error('Message content is required');
  }
  
  const finalOptions = {
    ...DEFAULT_SMS_OPTIONS,
    ...options
  };
  
  return await ExpoSmsManagerModule.sendLongSms(phoneNumber.trim(), message, finalOptions);
}

/**
 * Gets information about available SIM cards
 * @returns Array of SIM card information
 */
export function getAvailableSimCards(): SimCardInfo[] {
  if (!isSupported()) {
    return [];
  }
  return ExpoSmsManagerModule.getAvailableSimCards();
}

/**
 * Checks the signal strength of a specific SIM slot
 * @param simSlot The SIM slot index (0 for first SIM, 1 for second)
 * @returns Promise that resolves with signal strength information
 */
export async function checkSignalStrength(simSlot: number = 0): Promise<SignalStrengthInfo> {
  if (!isSupported()) {
    throw new Error('SMS operations are only supported on Android platform');
  }
  
  return await ExpoSmsManagerModule.checkSignalStrength(simSlot);
}

// ===== READING FUNCTIONS =====

/**
 * Starts listening for incoming SMS messages
 * @returns Promise that resolves with success message
 * @throws Error if permissions are not granted or if starting fails
 */
export async function startSmsListener(): Promise<string> {
  if (!isSupported()) {
    throw new Error('SMS operations are only supported on Android platform');
  }
  return await ExpoSmsManagerModule.startSmsListener();
}

/**
 * Stops listening for incoming SMS messages
 * @returns Promise that resolves with success message
 */
export async function stopSmsListener(): Promise<string> {
  if (!isSupported()) {
    throw new Error('SMS operations are only supported on Android platform');
  }
  return await ExpoSmsManagerModule.stopSmsListener();
}

/**
 * Retrieves SMS messages from a specific phone number
 * @param phoneNumber The phone number to filter messages from
 * @param limit Maximum number of messages to retrieve (default: 10)
 * @returns Promise that resolves with array of SMS messages
 */
export async function getSmsFromNumber(phoneNumber: string, limit: number = 10): Promise<SmsMessage[]> {
  if (!isSupported()) {
    throw new Error('SMS operations are only supported on Android platform');
  }
  
  if (!phoneNumber || phoneNumber.trim() === '') {
    throw new Error('Phone number is required');
  }
  
  return await ExpoSmsManagerModule.getSmsFromNumber(phoneNumber.trim(), Math.max(1, limit));
}

/**
 * Retrieves the most recent SMS messages
 * @param limit Maximum number of messages to retrieve (default: 10)
 * @returns Promise that resolves with array of recent SMS messages
 */
export async function getRecentSms(limit: number = 10): Promise<SmsMessage[]> {
  if (!isSupported()) {
    throw new Error('SMS operations are only supported on Android platform');
  }
  
  return await ExpoSmsManagerModule.getRecentSms(Math.max(1, limit));
}

/**
 * Finds SMS messages containing specific text
 * @param searchText Text to search for in SMS messages
 * @param limit Maximum number of messages to retrieve (default: 10)
 * @returns Promise that resolves with array of matching SMS messages
 */
export async function findSmsWithText(searchText: string, limit: number = 10): Promise<SmsMessage[]> {
  if (!isSupported()) {
    throw new Error('SMS operations are only supported on Android platform');
  }
  
  if (!searchText || searchText.trim() === '') {
    throw new Error('Search text is required');
  }
  
  return await ExpoSmsManagerModule.findSmsWithText(searchText.trim(), Math.max(1, limit));
}

// ===== EVENT LISTENERS =====

/**
 * Adds a listener for SMS received events
 * @param listener Function to call when SMS is received
 * @returns EventSubscription object for removing the listener
 */
export function addSmsListener(listener: ExpoSmsManagerModuleEvents['onSmsReceived']): EventSubscription {
  return ExpoSmsManagerModule.addListener('onSmsReceived', listener);
}

/**
 * Adds a listener for SMS reader error events
 * @param listener Function to call when an error occurs
 * @returns EventSubscription object for removing the listener
 */
export function addErrorListener(listener: ExpoSmsManagerModuleEvents['onError']): EventSubscription {
  return ExpoSmsManagerModule.addListener('onError', listener);
}

/**
 * Adds a listener for SMS send progress events
 * @param listener Function to call when send progress updates
 * @returns EventSubscription object for removing the listener
 */
export function addSmsProgressListener(listener: ExpoSmsManagerModuleEvents['onSmsProgress']): EventSubscription {
  return ExpoSmsManagerModule.addListener('onSmsProgress', listener);
}

/**
 * Adds a listener for SMS sent confirmation events
 * @param listener Function to call when SMS is sent
 * @returns EventSubscription object for removing the listener
 */
export function addSmsSentListener(listener: ExpoSmsManagerModuleEvents['onSmsSent']): EventSubscription {
  return ExpoSmsManagerModule.addListener('onSmsSent', listener);
}

/**
 * Adds a listener for SMS delivered confirmation events
 * @param listener Function to call when SMS is delivered
 * @returns EventSubscription object for removing the listener
 */
export function addSmsDeliveredListener(listener: ExpoSmsManagerModuleEvents['onSmsDelivered']): EventSubscription {
  return ExpoSmsManagerModule.addListener('onSmsDelivered', listener);
}

// ===== UTILITY FUNCTIONS =====

/**
 * Utility function to extract OTP from SMS message
 * @param message SMS message text
 * @param length Expected OTP length (default: 4-8 digits)
 * @returns Extracted OTP string or null if not found
 */
export function extractOtp(message: string, length?: number): string | null {
  if (!message) return null;
  
  // Common OTP patterns
  const patterns = [
    /\b\d{4,8}\b/, // Generic 4-8 digits
    /OTP[:\s]+(\d{4,8})/i, // OTP: 123456
    /code[:\s]+(\d{4,8})/i, // Code: 123456
    /verification code[:\s]+(\d{4,8})/i, // Verification code: 123456
  ];
  
  // If specific length is provided, use it
  if (length && length > 0) {
    patterns.unshift(new RegExp(`\\b\\d{${length}}\\b`));
  }
  
  for (const pattern of patterns) {
    const match = message.match(pattern);
    if (match) {
      // Return the captured group if exists, otherwise the full match
      return match[1] || match[0];
    }
  }
  
  return null;
}

/**
 * Utility function to check if SMS is from a specific sender pattern
 * @param sender Sender address/number
 * @param pattern Pattern to match (can include wildcards with *)
 * @returns true if sender matches pattern
 */
export function matchesSenderPattern(sender: string, pattern: string): boolean {
  if (!sender || !pattern) return false;
  
  // Convert wildcard pattern to regex
  const regexPattern = pattern
    .replace(/[.*+?^${}()|[\]\\]/g, '\\$&') // Escape special regex chars
    .replace(/\\\*/g, '.*'); // Convert * to .*
  
  const regex = new RegExp(`^${regexPattern}$`, 'i');
  return regex.test(sender);
}

/**
 * Utility function to validate phone number format
 * @param phoneNumber Phone number to validate
 * @returns true if phone number appears valid
 */
export function isValidPhoneNumber(phoneNumber: string): boolean {
  if (!phoneNumber) return false;
  
  // Basic validation - adjust regex based on your needs
  const phoneRegex = /^[\+]?[(]?[0-9]{1,4}[)]?[-\s\.]?[(]?[0-9]{1,4}[)]?[-\s\.]?[0-9]{1,9}$/;
  return phoneRegex.test(phoneNumber.trim());
}

/**
 * Utility function to format phone number for sending
 * @param phoneNumber Phone number to format
 * @param countryCode Optional country code to prepend
 * @returns Formatted phone number
 */
export function formatPhoneNumber(phoneNumber: string, countryCode?: string): string {
  if (!phoneNumber) return '';
  
  // Remove all non-numeric characters except +
  let cleaned = phoneNumber.replace(/[^\d+]/g, '');
  
  // Add country code if provided and not already present
  if (countryCode && !cleaned.startsWith('+')) {
    cleaned = countryCode + cleaned;
  }
  
  return cleaned;
}

/**
 * Utility to check if message will be sent as multipart
 * @param message The message text
 * @returns true if message will be split into multiple parts
 */
export function willBeSentAsMultipart(message: string): boolean {
  if (!message) return false;
  
  // Check if message contains unicode characters
  const hasUnicode = /[^\x00-\x7F]/.test(message);
  
  // Different length limits for standard vs unicode
  const maxLength = hasUnicode ? 70 : 160;
  
  return message.length > maxLength;
}

/**
 * Calculate number of SMS parts for a message
 * @param message The message text
 * @returns Number of SMS parts the message will be split into
 */
export function calculateSmsParts(message: string): number {
  if (!message) return 0;
  
  const hasUnicode = /[^\x00-\x7F]/.test(message);
  
  if (hasUnicode) {
    // Unicode messages
    if (message.length <= 70) return 1;
    return Math.ceil(message.length / 67);
  } else {
    // Standard GSM messages
    if (message.length <= 160) return 1;
    return Math.ceil(message.length / 153);
  }
}

// Default export for convenience
export default {
  // Core functions
  isSupported,
  hasPermissions,
  
  // Sending functions
  sendSms,
  sendSmsWithTracking,
  sendSmsAndWaitForDelivery,
  sendSmsToMultiple,
  sendLongSms,
  getAvailableSimCards,
  checkSignalStrength,
  
  // Reading functions
  startSmsListener,
  stopSmsListener,
  getSmsFromNumber,
  getRecentSms,
  findSmsWithText,
  
  // Event listeners
  addSmsListener,
  addErrorListener,
  addSmsProgressListener,
  addSmsSentListener,
  addSmsDeliveredListener,
  
  // Utility functions
  extractOtp,
  matchesSenderPattern,
  isValidPhoneNumber,
  formatPhoneNumber,
  willBeSentAsMultipart,
  calculateSmsParts,
};