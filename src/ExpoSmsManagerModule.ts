import { NativeModule, requireNativeModule } from 'expo';
import { 
  ExpoSmsManagerModuleEvents, 
  SmsMessage,
  SmsSendResult,
  SmsMultipleResult,
  SimCardInfo,
  SignalStrengthInfo
} from './ExpoSmsManager.types';

declare class ExpoSmsManagerModule extends NativeModule<ExpoSmsManagerModuleEvents> {
  // Sending methods
  sendSms(phoneNumber: string, message: string, options: Record<string, any>): Promise<SmsSendResult>;
  sendSmsToMultiple(phoneNumbers: string[], message: string, options: Record<string, any>): Promise<SmsMultipleResult[]>;
  sendLongSms(phoneNumber: string, message: string, options: Record<string, any>): Promise<SmsSendResult>;
  
  // SIM and signal methods
  getAvailableSimCards(): SimCardInfo[];
  checkSignalStrength(simSlot: number): Promise<SignalStrengthInfo>;
  
  // Reading methods (from original)
  startSmsListener(): Promise<string>;
  stopSmsListener(): Promise<string>;
  hasPermissions(): boolean;
  getSmsFromNumber(phoneNumber: string, limit: number): Promise<SmsMessage[]>;
  getRecentSms(limit: number): Promise<SmsMessage[]>;
  findSmsWithText(searchText: string, limit: number): Promise<SmsMessage[]>;
}

export default requireNativeModule<ExpoSmsManagerModule>('ExpoSmsManager');