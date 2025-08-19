// ===== CORE TYPES =====

export interface SmsMessage {
  id?: number;
  sender: string;
  message: string;
  timestamp: number;
  date: string;
  type?: 'received' | 'sent';
}

export interface SmsError {
  error: string;
  message: string;
}

// ===== SENDING TYPES WITH FIX =====

export interface SmsSendOptions {
  /**
   * SIM slot to use for sending (0 for first SIM, 1 for second)
   * Default: 0
   */
  simSlot?: number;
  
  /**
   * Whether to request delivery report
   * Default: false
   * Note: Set to false for immediate response. Delivery reports may not always arrive.
   */
  requestStatusReport?: boolean;
  
  /**
   * Whether to check signal strength before sending
   * Default: false
   */
  checkSignal?: boolean;
  
  /**
   * Whether to wait for delivery confirmation before resolving promise
   * Only used when requestStatusReport is true
   * Default: false (resolves immediately after sent status)
   * 
   * When false: Promise resolves as soon as SMS is sent to network
   * When true: Promise waits for delivery confirmation (with timeout)
   */
  waitForDelivery?: boolean;
  
  /**
   * Timeout in milliseconds to wait for delivery confirmation
   * Only used when waitForDelivery is true
   * Default: 30000 (30 seconds)
   */
  deliveryTimeout?: number;
  
  /**
   * Minimum signal strength required (1-4)
   * Only used if checkSignal is true
   * Default: 2 (moderate)
   */
  minSignalStrength?: number;
  
  /**
   * Custom metadata to attach to the message
   */
  metadata?: Record<string, any>;
}

export interface SmsSendResult {
  /**
   * Unique identifier for the sent message
   */
  messageId: string;
  
  /**
   * Sending status
   * - 'sent': Successfully sent to network
   * - 'sent_no_confirmation': Sent without waiting for network confirmation
   * - 'generic_failure': General sending failure
   * - 'no_service': No network service available
   * - 'null_pdu': Invalid message format
   * - 'radio_off': Radio/airplane mode is on
   * - 'unknown_error': Unknown error occurred
   * - 'pending': Still being processed
   */
  sent: 'sent' | 'sent_no_confirmation' | 'generic_failure' | 'no_service' | 'null_pdu' | 'radio_off' | 'unknown_error' | 'pending';
  
  /**
   * Delivery status (if requested)
   * - 'delivered': Message delivered to recipient
   * - 'pending': Awaiting delivery confirmation
   * - 'not_requested': Delivery tracking not requested
   * - 'failed': Delivery failed
   * - 'timeout': Delivery confirmation timed out
   */
  delivered?: 'delivered' | 'pending' | 'not_requested' | 'failed' | 'timeout';
  
  /**
   * Additional result details
   */
  resultCode?: number;
  
  /**
   * Error message if failed
   */
  error?: string;
  
  /**
   * Status message for display
   */
  status?: string;
}

export interface SmsMultipleResult {
  /**
   * Phone number
   */
  number: string;
  
  /**
   * Message ID if initiated successfully
   */
  messageId?: string;
  
  /**
   * Status of the send attempt
   */
  status: 'initiated' | 'failed' | 'sent';
  
  /**
   * Error message if failed
   */
  error?: string;
}

// ===== SIM CARD TYPES =====

export interface SimCardInfo {
  /**
   * SIM slot index (0-based)
   */
  slot: number;
  
  /**
   * Physical SIM slot index
   */
  simSlotIndex?: number;
  
  /**
   * Display name of the SIM (e.g., "SIM 1", "Work")
   */
  displayName: string;
  
  /**
   * Carrier name
   */
  carrierName?: string;
  
  /**
   * Subscription ID (Android internal)
   */
  subscriptionId?: number;
  
  /**
   * Country ISO code
   */
  countryIso?: string;
  
  /**
   * Whether the SIM is currently active
   */
  isActive: boolean;
  
  /**
   * Phone number associated with SIM (if available)
   */
  phoneNumber?: string;
}

export interface SignalStrengthInfo {
  /**
   * SIM slot index
   */
  simSlot: number;
  
  /**
   * Signal strength value (0-4, -1 for unknown)
   */
  signalStrength: number;
  
  /**
   * Human-readable signal level
   */
  signalLevel: 'none' | 'poor' | 'moderate' | 'good' | 'excellent' | 'unknown';
  
  /**
   * Whether there is any signal
   */
  hasSignal: boolean;
  
  /**
   * Additional signal metrics (if available)
   */
  metrics?: {
    dbm?: number;
    asu?: number;
    rssi?: number;
  };
}

// ===== EVENT TYPES =====

export interface SmsProgressEvent {
  /**
   * Message ID
   */
  messageId: string;
  
  /**
   * Current status
   */
  status: 'sending' | 'sending_multipart' | 'sent' | 'failed' | 'delivered';
  
  /**
   * Phone number being sent to
   */
  phoneNumber?: string;
  
  /**
   * Number of parts (for multipart messages)
   */
  parts?: number;
  
  /**
   * Current part being sent
   */
  currentPart?: number;
  
  /**
   * Progress percentage (0-100)
   */
  progress?: number;
}

export interface SmsSentEvent {
  /**
   * Message ID
   */
  messageId: string;
  
  /**
   * Sending status
   */
  status: 'sent' | 'generic_failure' | 'no_service' | 'null_pdu' | 'radio_off' | 'unknown_error';
  
  /**
   * Android result code
   */
  resultCode: number;
  
  /**
   * Part index (for multipart messages)
   */
  part?: number;
  
  /**
   * Total parts (for multipart messages)
   */
  totalParts?: number;
}

export interface SmsDeliveredEvent {
  /**
   * Message ID
   */
  messageId: string;
  
  /**
   * Delivery status
   */
  status: 'delivered' | 'failed';
  
  /**
   * Part index (for multipart messages)
   */
  part?: number;
  
  /**
   * Total parts (for multipart messages)
   */
  totalParts?: number;
  
  /**
   * Delivery timestamp
   */
  timestamp?: number;
}

// ===== MODULE EVENTS TYPE =====

export type ExpoSmsManagerModuleEvents = {
  /**
   * Fired when an SMS is received
   */
  onSmsReceived: (message: SmsMessage) => void;
  
  /**
   * Fired when an error occurs
   */
  onError: (error: SmsError) => void;
  
  /**
   * Fired when SMS sending progress updates
   */
  onSmsProgress: (event: SmsProgressEvent) => void;
  
  /**
   * Fired when SMS is sent (confirmed by network)
   */
  onSmsSent: (event: SmsSentEvent) => void;
  
  /**
   * Fired when SMS is delivered to recipient
   */
  onSmsDelivered: (event: SmsDeliveredEvent) => void;
};

// ===== PERMISSION TYPES =====

export interface SmsPermissions {
  /**
   * Permission to read SMS messages
   */
  READ_SMS: boolean;
  
  /**
   * Permission to receive SMS messages
   */
  RECEIVE_SMS: boolean;
  
  /**
   * Permission to send SMS messages
   */
  SEND_SMS: boolean;
  
  /**
   * Permission to read phone state (for dual SIM)
   */
  READ_PHONE_STATE: boolean;
}

// ===== ERROR CODES =====

export enum SmsErrorCode {
  PERMISSION_ERROR = 'PERMISSION_ERROR',
  SEND_ERROR = 'SEND_ERROR',
  SIGNAL_ERROR = 'SIGNAL_ERROR',
  NO_SIM_ERROR = 'NO_SIM_ERROR',
  INVALID_NUMBER = 'INVALID_NUMBER',
  MESSAGE_TOO_LONG = 'MESSAGE_TOO_LONG',
  NETWORK_ERROR = 'NETWORK_ERROR',
  UNKNOWN_ERROR = 'UNKNOWN_ERROR',
}

// ===== CONSTANTS =====

export const SMS_MAX_LENGTH = 160;
export const SMS_MAX_LENGTH_UNICODE = 70;
export const SMS_MULTIPART_MAX_LENGTH = 153;
export const SMS_MULTIPART_MAX_LENGTH_UNICODE = 67;

// ===== DEFAULT OPTIONS =====

export const DEFAULT_SMS_OPTIONS: SmsSendOptions = {
  simSlot: 0,
  requestStatusReport: false,  // Default to false for immediate response
  checkSignal: false,
  waitForDelivery: false,
  deliveryTimeout: 30000,
  minSignalStrength: 2,
};