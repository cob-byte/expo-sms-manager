package expo.modules.smsmanager

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.Promise
import java.text.SimpleDateFormat
import java.util.*

class ExpoSmsManagerModule : Module() {
  private var smsReceiver: BroadcastReceiver? = null
  private var sentReceiver: BroadcastReceiver? = null
  private var deliveredReceiver: BroadcastReceiver? = null
  private val TAG = "ExpoSmsManager"
  private val pendingSendPromises = mutableMapOf<String, Promise>()
  private val smsStatusMap = mutableMapOf<String, MutableMap<String, String>>()
  
  private val context: Context
    get() = appContext.reactContext ?: throw CodedException("ReactContext is null")
  
  override fun definition() = ModuleDefinition {
    Name("ExpoSmsManager")
    
    Events("onSmsReceived", "onError", "onSmsSent", "onSmsDelivered", "onSmsProgress")
    
    // ===== SENDING FUNCTIONS =====
    
    // Send SMS without opening app
    AsyncFunction("sendSms") { phoneNumber: String, message: String, options: Map<String, Any>?, promise: Promise ->
      try {
        if (!hasSendSmsPermission()) {
          promise.reject("PERMISSION_ERROR", "SEND_SMS permission not granted", null)
          return@AsyncFunction
        }
        
        val simSlot = options?.get("simSlot") as? Int ?: 0
        val requestStatusReport = options?.get("requestStatusReport") as? Boolean ?: true
        val checkSignal = options?.get("checkSignal") as? Boolean ?: false
        
        if (checkSignal && !hasGoodSignal(simSlot)) {
          promise.reject("SIGNAL_ERROR", "Poor signal strength on SIM $simSlot", null)
          return@AsyncFunction
        }
        
        sendSmsInternal(phoneNumber, message, simSlot, requestStatusReport, promise)
      } catch (error: Exception) {
        Log.e(TAG, "Error sending SMS", error)
        promise.reject("SEND_ERROR", error.message ?: "Unknown error", error)
      }
    }
    
    // Send SMS to multiple recipients
    AsyncFunction("sendSmsToMultiple") { phoneNumbers: List<String>, message: String, options: Map<String, Any>?, promise: Promise ->
      try {
        if (!hasSendSmsPermission()) {
          promise.reject("PERMISSION_ERROR", "SEND_SMS permission not granted", null)
          return@AsyncFunction
        }
        
        val results = mutableListOf<Map<String, Any>>()
        val simSlot = options?.get("simSlot") as? Int ?: 0
        
        for (number in phoneNumbers) {
          val messageId = UUID.randomUUID().toString()
          try {
            sendSmsWithoutPromise(number, message, simSlot, messageId)
            results.add(mapOf(
              "number" to number,
              "messageId" to messageId,
              "status" to "initiated"
            ))
          } catch (e: Exception) {
            results.add(mapOf(
              "number" to number,
              "status" to "failed",
              "error" to (e.message ?: "Unknown error")
            ))
          }
        }
        
        promise.resolve(results)
      } catch (error: Exception) {
        Log.e(TAG, "Error sending multiple SMS", error)
        promise.reject("SEND_MULTIPLE_ERROR", error.message ?: "Unknown error", error)
      }
    }
    
    // Send long SMS (multipart)
    AsyncFunction("sendLongSms") { phoneNumber: String, message: String, options: Map<String, Any>?, promise: Promise ->
      try {
        if (!hasSendSmsPermission()) {
          promise.reject("PERMISSION_ERROR", "SEND_SMS permission not granted", null)
          return@AsyncFunction
        }
        
        val simSlot = options?.get("simSlot") as? Int ?: 0
        sendMultipartSms(phoneNumber, message, simSlot, promise)
      } catch (error: Exception) {
        Log.e(TAG, "Error sending long SMS", error)
        promise.reject("SEND_LONG_ERROR", error.message ?: "Unknown error", error)
      }
    }
    
    // Get available SIM cards
    Function("getAvailableSimCards") {
      return@Function getSimCardInfo()
    }
    
    // Check signal strength
    AsyncFunction("checkSignalStrength") { simSlot: Int, promise: Promise ->
      try {
        val signalInfo = getSignalStrength(simSlot)
        promise.resolve(signalInfo)
      } catch (error: Exception) {
        Log.e(TAG, "Error checking signal", error)
        promise.reject("SIGNAL_CHECK_ERROR", error.message ?: "Unknown error", error)
      }
    }
    
    // ===== READING FUNCTIONS (from original) =====
    
    AsyncFunction("startSmsListener") { promise: Promise ->
      try {
        Log.d(TAG, "Starting SMS listener...")
        
        if (!hasReceiveSmsPermission()) {
          Log.e(TAG, "Required SMS permissions not granted")
          promise.reject("PERMISSION_ERROR", "Required SMS permissions not granted", null)
          return@AsyncFunction
        }
        
        startListening()
        Log.d(TAG, "SMS listener started successfully")
        promise.resolve("SMS listener started successfully")
      } catch (error: Exception) {
        Log.e(TAG, "Error starting SMS listener", error)
        promise.reject("START_ERROR", error.message ?: "Unknown error", error)
      }
    }
    
    AsyncFunction("stopSmsListener") { promise: Promise ->
      try {
        Log.d(TAG, "Stopping SMS listener...")
        stopListening()
        Log.d(TAG, "SMS listener stopped successfully")
        promise.resolve("SMS listener stopped successfully")
      } catch (error: Exception) {
        Log.e(TAG, "Error stopping SMS listener", error)
        promise.reject("STOP_ERROR", error.message ?: "Unknown error", error)
      }
    }
    
    Function("hasPermissions") {
      return@Function hasAllPermissions()
    }
    
    AsyncFunction("getSmsFromNumber") { phoneNumber: String, limit: Int, promise: Promise ->
      try {
        if (hasReadSmsPermission()) {
          val messages = getSmsMessages(phoneNumber, limit)
          promise.resolve(messages)
        } else {
          promise.reject("PERMISSION_ERROR", "READ_SMS permission not granted", null)
        }
      } catch (error: Exception) {
        Log.e(TAG, "Error getting SMS messages", error)
        promise.reject("GET_SMS_ERROR", error.message ?: "Unknown error", error)
      }
    }
    
    AsyncFunction("getRecentSms") { limit: Int, promise: Promise ->
      try {
        if (hasReadSmsPermission()) {
          val messages = getRecentSmsMessages(limit)
          promise.resolve(messages)
        } else {
          promise.reject("PERMISSION_ERROR", "READ_SMS permission not granted", null)
        }
      } catch (error: Exception) {
        Log.e(TAG, "Error getting recent SMS messages", error)
        promise.reject("GET_RECENT_SMS_ERROR", error.message ?: "Unknown error", error)
      }
    }
    
    AsyncFunction("findSmsWithText") { searchText: String, limit: Int, promise: Promise ->
      try {
        if (hasReadSmsPermission()) {
          val messages = findSmsContainingText(searchText, limit)
          promise.resolve(messages)
        } else {
          promise.reject("PERMISSION_ERROR", "READ_SMS permission not granted", null)
        }
      } catch (error: Exception) {
        Log.e(TAG, "Error searching SMS messages", error)
        promise.reject("SEARCH_SMS_ERROR", error.message ?: "Unknown error", error)
      }
    }
    
    OnDestroy {
      Log.d(TAG, "Module being destroyed, cleaning up...")
      stopListening()
      stopSendListeners()
    }
  }
  
  // ===== SENDING IMPLEMENTATION =====
  
  private fun sendSmsInternal(phoneNumber: String, message: String, simSlot: Int, requestStatusReport: Boolean, promise: Promise) {
    val messageId = UUID.randomUUID().toString()
    pendingSendPromises[messageId] = promise
    smsStatusMap[messageId] = mutableMapOf(
      "sent" to "pending",
      "delivered" to if (requestStatusReport) "pending" else "not_requested"
    )
    
    try {
      val smsManager = getSmsManagerForSim(simSlot)
      
      if (!requestStatusReport) {
        // Simple send without status tracking
        smsManager.sendTextMessage(phoneNumber, null, message, null, null)
        promise.resolve(bundleOf(
          "messageId" to messageId,
          "status" to "sent_no_confirmation"
        ))
        pendingSendPromises.remove(messageId)
        return
      }
      
      setupSendStatusListeners()
      
      val sentPI = PendingIntent.getBroadcast(
        context, 
        messageId.hashCode(),
        Intent("SMS_SENT").apply { putExtra("messageId", messageId) },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      )
      
      val deliveredPI = PendingIntent.getBroadcast(
        context,
        messageId.hashCode() + 1,
        Intent("SMS_DELIVERED").apply { putExtra("messageId", messageId) },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      )
      
      sendEvent("onSmsProgress", bundleOf(
        "messageId" to messageId,
        "status" to "sending",
        "phoneNumber" to phoneNumber
      ))
      
      smsManager.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI)
      
    } catch (e: Exception) {
      pendingSendPromises.remove(messageId)
      smsStatusMap.remove(messageId)
      throw e
    }
  }
  
  private fun sendSmsWithoutPromise(phoneNumber: String, message: String, simSlot: Int, messageId: String) {
    val smsManager = getSmsManagerForSim(simSlot)
    
    val sentPI = PendingIntent.getBroadcast(
      context,
      messageId.hashCode(),
      Intent("SMS_SENT").apply { putExtra("messageId", messageId) },
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
    
    smsManager.sendTextMessage(phoneNumber, null, message, sentPI, null)
  }
  
  private fun sendMultipartSms(phoneNumber: String, message: String, simSlot: Int, promise: Promise) {
    val messageId = UUID.randomUUID().toString()
    val smsManager = getSmsManagerForSim(simSlot)
    val parts = smsManager.divideMessage(message)
    
    if (parts.size == 1) {
      sendSmsInternal(phoneNumber, message, simSlot, true, promise)
      return
    }
    
    pendingSendPromises[messageId] = promise
    setupSendStatusListeners()
    
    val sentIntents = ArrayList<PendingIntent>()
    val deliveredIntents = ArrayList<PendingIntent>()
    
    for (i in parts.indices) {
      val sentPI = PendingIntent.getBroadcast(
        context,
        messageId.hashCode() + i,
        Intent("SMS_SENT").apply { 
          putExtra("messageId", messageId)
          putExtra("part", i)
          putExtra("totalParts", parts.size)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      )
      sentIntents.add(sentPI)
      
      val deliveredPI = PendingIntent.getBroadcast(
        context,
        messageId.hashCode() + parts.size + i,
        Intent("SMS_DELIVERED").apply {
          putExtra("messageId", messageId)
          putExtra("part", i)
          putExtra("totalParts", parts.size)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      )
      deliveredIntents.add(deliveredPI)
    }
    
    sendEvent("onSmsProgress", bundleOf(
      "messageId" to messageId,
      "status" to "sending_multipart",
      "parts" to parts.size
    ))
    
    smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, deliveredIntents)
  }
  
  private fun setupSendStatusListeners() {
    if (sentReceiver == null) {
      sentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          val messageId = intent.getStringExtra("messageId") ?: return
          val resultCode = resultCode
          
          val status = when (resultCode) {
            android.app.Activity.RESULT_OK -> "sent"
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "generic_failure"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "no_service"
            SmsManager.RESULT_ERROR_NULL_PDU -> "null_pdu"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "radio_off"
            else -> "unknown_error"
          }
          
          smsStatusMap[messageId]?.set("sent", status)
          
          sendEvent("onSmsSent", bundleOf(
            "messageId" to messageId,
            "status" to status,
            "resultCode" to resultCode
          ))
          
          checkAndResolvePromise(messageId)
        }
      }
      
      val sentFilter = IntentFilter("SMS_SENT")
      context.registerReceiver(sentReceiver, sentFilter, Context.RECEIVER_NOT_EXPORTED)
    }
    
    if (deliveredReceiver == null) {
      deliveredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          val messageId = intent.getStringExtra("messageId") ?: return
          
          smsStatusMap[messageId]?.set("delivered", "delivered")
          
          sendEvent("onSmsDelivered", bundleOf(
            "messageId" to messageId,
            "status" to "delivered"
          ))
          
          checkAndResolvePromise(messageId)
        }
      }
      
      val deliveredFilter = IntentFilter("SMS_DELIVERED")
      context.registerReceiver(deliveredReceiver, deliveredFilter, Context.RECEIVER_NOT_EXPORTED)
    }
  }
  
  private fun checkAndResolvePromise(messageId: String) {
    val promise = pendingSendPromises[messageId] ?: return
    val status = smsStatusMap[messageId] ?: return
    
    val sentStatus = status["sent"] ?: "pending"
    val deliveredStatus = status["delivered"] ?: "pending"
    
    // If sent status is determined and delivery is not requested or determined
    if (sentStatus != "pending" && (deliveredStatus == "not_requested" || deliveredStatus != "pending")) {
      promise.resolve(bundleOf(
        "messageId" to messageId,
        "sent" to sentStatus,
        "delivered" to deliveredStatus
      ))
      pendingSendPromises.remove(messageId)
      smsStatusMap.remove(messageId)
    }
  }
  
  private fun stopSendListeners() {
    sentReceiver?.let {
      try {
        context.unregisterReceiver(it)
      } catch (e: Exception) {
        Log.w(TAG, "Error unregistering sent receiver", e)
      }
    }
    sentReceiver = null
    
    deliveredReceiver?.let {
      try {
        context.unregisterReceiver(it)
      } catch (e: Exception) {
        Log.w(TAG, "Error unregistering delivered receiver", e)
      }
    }
    deliveredReceiver = null
  }
  
  private fun getSmsManagerForSim(simSlot: Int): SmsManager {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
      val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
      
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
        val subscriptionInfoList = subscriptionManager.activeSubscriptionInfoList
        
        if (subscriptionInfoList != null && simSlot < subscriptionInfoList.size) {
          val subscriptionId = subscriptionInfoList[simSlot].subscriptionId
          
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
          } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
          }
        } else {
          SmsManager.getDefault()
        }
      } else {
        SmsManager.getDefault()
      }
    } else {
      SmsManager.getDefault()
    }
  }
  
  private fun getSimCardInfo(): List<Map<String, Any>> {
    val simInfo = mutableListOf<Map<String, Any>>()
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        val subscriptionInfoList = subscriptionManager.activeSubscriptionInfoList
        
        subscriptionInfoList?.forEachIndexed { index, info ->
          simInfo.add(mapOf(
            "slot" to index,
            "simSlotIndex" to info.simSlotIndex,
            "displayName" to info.displayName.toString(),
            "carrierName" to info.carrierName.toString(),
            "subscriptionId" to info.subscriptionId,
            "countryIso" to info.countryIso,
            "isActive" to true
          ))
        }
      }
    }
    
    if (simInfo.isEmpty()) {
      // Fallback for single SIM or no permission
      simInfo.add(mapOf(
        "slot" to 0,
        "displayName" to "Default SIM",
        "isActive" to true
      ))
    }
    
    return simInfo
  }
  
  private fun getSignalStrength(simSlot: Int): Map<String, Any> {
    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    var signalStrength = -1
    var signalLevel = "unknown"
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
        signalStrength = telephonyManager.signalStrength?.level ?: -1
        signalLevel = when (signalStrength) {
          4 -> "excellent"
          3 -> "good"
          2 -> "moderate"
          1 -> "poor"
          0 -> "none"
          else -> "unknown"
        }
      }
    }
    
    return mapOf(
      "simSlot" to simSlot,
      "signalStrength" to signalStrength,
      "signalLevel" to signalLevel,
      "hasSignal" to (signalStrength > 0)
    )
  }
  
  private fun hasGoodSignal(simSlot: Int): Boolean {
    val signal = getSignalStrength(simSlot)
    val strength = signal["signalStrength"] as? Int ?: -1
    return strength >= 2 // Moderate or better signal
  }
  
  // ===== PERMISSION HELPERS =====
  
  private fun hasAllPermissions(): Boolean {
    return hasReadSmsPermission() && hasReceiveSmsPermission() && hasSendSmsPermission()
  }
  
  private fun hasReadSmsPermission(): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
  }
  
  private fun hasReceiveSmsPermission(): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
  }
  
  private fun hasSendSmsPermission(): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
  }
  
  // ===== ORIGINAL READING FUNCTIONS (kept from your original code) =====
  
  private fun startListening() {
    Log.d(TAG, "startListening() called")
    
    if (smsReceiver != null) {
      Log.w(TAG, "SMS receiver already registered, stopping first")
      stopListening()
    }
    
    try {
      smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          Log.d(TAG, "SMS broadcast received with action: ${intent.action}")
          
          if (intent.action != "android.provider.Telephony.SMS_RECEIVED") {
            Log.w(TAG, "Received unexpected action: ${intent.action}")
            return
          }
          
          try {
            val smsData = extractSmsFromIntent(intent)
            if (smsData != null) {
              Log.d(TAG, "Sending SMS data to React Native")
              sendEvent("onSmsReceived", smsData)
            } else {
              Log.w(TAG, "Failed to extract SMS data from intent")
            }
          } catch (error: Exception) {
            Log.e(TAG, "Error processing received SMS", error)
            try {
              sendEvent("onError", bundleOf(
                "error" to "PROCESSING_ERROR",
                "message" to (error.message ?: "Unknown error processing SMS")
              ))
            } catch (eventError: Exception) {
              Log.e(TAG, "Error sending error event", eventError)
            }
          }
        }
      }
      
      val intentFilter = IntentFilter().apply {
        addAction("android.provider.Telephony.SMS_RECEIVED")
        priority = 999
      }
      
      Log.d(TAG, "Registering broadcast receiver for API ${Build.VERSION.SDK_INT}...")
      
      when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
          try {
            context.registerReceiver(smsReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
            Log.i(TAG, "SMS receiver registered with RECEIVER_NOT_EXPORTED (API 33+)")
          } catch (e: Exception) {
            Log.w(TAG, "Failed with RECEIVER_NOT_EXPORTED, trying standard", e)
            context.registerReceiver(smsReceiver, intentFilter)
            Log.i(TAG, "SMS receiver registered with fallback")
          }
        }
        else -> {
          context.registerReceiver(smsReceiver, intentFilter)
          Log.i(TAG, "SMS receiver registered")
        }
      }
      
    } catch (error: Exception) {
      Log.e(TAG, "Critical error in startListening", error)
      smsReceiver = null
      throw error
    }
  }
  
  private fun stopListening() {
    Log.d(TAG, "stopListening() called")
    
    smsReceiver?.let { receiver ->
      try {
        context.unregisterReceiver(receiver)
        Log.i(TAG, "SMS receiver unregistered successfully")
      } catch (error: IllegalArgumentException) {
        Log.w(TAG, "Receiver was not registered", error)
      } catch (error: Exception) {
        Log.e(TAG, "Error unregistering SMS receiver", error)
      }
    }
    smsReceiver = null
  }
  
  private fun extractSmsFromIntent(intent: Intent): Bundle? {
    // [Your original extractSmsFromIntent implementation]
    // Keeping the same as your original code
    val bundle = intent.extras ?: return null
    
    try {
      val pdusObj = bundle.get("pdus") as? Array<*> ?: return null
      val format = bundle.getString("format") ?: "3gpp"
      
      var sender = ""
      val messageBuilder = StringBuilder()
      var timestamp = System.currentTimeMillis()
      
      for (i in pdusObj.indices) {
        val pdu = pdusObj[i] as? ByteArray ?: continue
        
        val currentMessage = try {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            SmsMessage.createFromPdu(pdu, format)
          } else {
            @Suppress("DEPRECATION")
            SmsMessage.createFromPdu(pdu)
          }
        } catch (e: Exception) {
          null
        } ?: continue
        
        if (sender.isEmpty()) {
          sender = currentMessage.displayOriginatingAddress ?: currentMessage.originatingAddress ?: ""
          timestamp = currentMessage.timestampMillis
        }
        
        val body = currentMessage.displayMessageBody ?: currentMessage.messageBody
        if (!body.isNullOrEmpty()) {
          messageBuilder.append(body)
        }
      }
      
      val message = messageBuilder.toString()
      
      if (sender.isEmpty() || message.isEmpty()) {
        return null
      }
      
      return bundleOf(
        "sender" to sender,
        "message" to message,
        "timestamp" to timestamp,
        "date" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
      )
      
    } catch (error: Exception) {
      Log.e(TAG, "Error extracting SMS from intent", error)
      return null
    }
  }
  
  private fun getSmsMessages(phoneNumber: String, limit: Int): List<Bundle> {
    // [Your original implementation - keeping the same]
    val messages = mutableListOf<Bundle>()
    
    if (!hasReadSmsPermission()) {
      return messages
    }
    
    val uri = Telephony.Sms.CONTENT_URI
    val projection = arrayOf(
      Telephony.Sms._ID,
      Telephony.Sms.ADDRESS,
      Telephony.Sms.BODY,
      Telephony.Sms.DATE,
      Telephony.Sms.TYPE
    )
    
    val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
    val selection = "${Telephony.Sms.ADDRESS} LIKE ?"
    val selectionArgs = arrayOf("%$cleanNumber%")
    val sortOrder = "${Telephony.Sms.DATE} DESC"
    
    var cursor: Cursor? = null
    try {
      cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
      
      cursor?.let { c ->
        var count = 0
        while (c.moveToNext() && count < limit) {
          try {
            val id = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms._ID))
            val address = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
            val body = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
            val date = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.DATE))
            val type = c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.TYPE))
            
            messages.add(bundleOf(
              "id" to id,
              "sender" to address,
              "message" to body,
              "timestamp" to date,
              "date" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(date)),
              "type" to if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) "received" else "sent"
            ))
            count++
          } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS record", e)
          }
        }
      }
    } catch (error: Exception) {
      Log.e(TAG, "Error querying SMS messages", error)
      throw error
    } finally {
      cursor?.close()
    }
    
    return messages
  }
  
  private fun getRecentSmsMessages(limit: Int): List<Bundle> {
    // [Your original implementation]
    val messages = mutableListOf<Bundle>()
    
    if (!hasReadSmsPermission()) {
      return messages
    }
    
    val uri = Telephony.Sms.CONTENT_URI
    val projection = arrayOf(
      Telephony.Sms._ID,
      Telephony.Sms.ADDRESS,
      Telephony.Sms.BODY,
      Telephony.Sms.DATE,
      Telephony.Sms.TYPE
    )
    
    val sortOrder = "${Telephony.Sms.DATE} DESC"
    
    var cursor: Cursor? = null
    try {
      cursor = context.contentResolver.query(uri, projection, null, null, sortOrder)
      
      cursor?.let { c ->
        var count = 0
        while (c.moveToNext() && count < limit) {
          try {
            val id = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms._ID))
            val address = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
            val body = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
            val date = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.DATE))
            val type = c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.TYPE))
            
            messages.add(bundleOf(
              "id" to id,
              "sender" to address,
              "message" to body,
              "timestamp" to date,
              "date" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(date)),
              "type" to if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) "received" else "sent"
            ))
            count++
          } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS record", e)
          }
        }
      }
    } catch (error: Exception) {
      Log.e(TAG, "Error querying recent SMS messages", error)
      throw error
    } finally {
      cursor?.close()
    }
    
    return messages
  }
  
  private fun findSmsContainingText(searchText: String, limit: Int): List<Bundle> {
    // [Your original implementation]
    val messages = mutableListOf<Bundle>()
    
    if (!hasReadSmsPermission()) {
      return messages
    }
    
    val uri = Telephony.Sms.CONTENT_URI
    val projection = arrayOf(
      Telephony.Sms._ID,
      Telephony.Sms.ADDRESS,
      Telephony.Sms.BODY,
      Telephony.Sms.DATE,
      Telephony.Sms.TYPE
    )
    
    val selection = "${Telephony.Sms.BODY} LIKE ?"
    val selectionArgs = arrayOf("%$searchText%")
    val sortOrder = "${Telephony.Sms.DATE} DESC"
    
    var cursor: Cursor? = null
    try {
      cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
      
      cursor?.let { c ->
        var count = 0
        while (c.moveToNext() && count < limit) {
          try {
            val id = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms._ID))
            val address = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)) ?: ""
            val body = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY)) ?: ""
            val date = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.DATE))
            val type = c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.TYPE))
            
            messages.add(bundleOf(
              "id" to id,
              "sender" to address,
              "message" to body,
              "timestamp" to date,
              "date" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(date)),
              "type" to if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) "received" else "sent"
            ))
            count++
          } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS record", e)
          }
        }
      }
    } catch (error: Exception) {
      Log.e(TAG, "Error searching SMS messages", error)
      throw error
    } finally {
      cursor?.close()
    }
    
    return messages
  }
}