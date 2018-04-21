package bddevlabs.com.vivetrackerdemo;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Iterator;

public class ViveTrackerActivity extends AppCompatActivity {
    private TextView device_info_tv = null;
    UsbDevice htc_vive_tracker = null;
    UsbDeviceConnection vive_tracker_connection = null;

    private Handler vive_tracker_reset_handler = new Handler();

    public  static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    public  static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    public  static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";

    // Vive Tracker Data type
    static final int TRACKER_TRIGGER 		= 1 << 0;
    static final int TRACKER_BUMPER 		= 1 << 1;
    static final int TRACKER_MENU 		    = 1 << 2;
    static final int TRACKER_STEAM 		    = 1 << 3;
    static final int TRACKER_PAD_PRESS 	    = 1 << 4;
    static final int TRACKER_PAD_TOUCH 	    = 1 << 5;
    static final int TRACKER_RESERVED_1 	= 1 << 6;
    static final int TRACKER_RESERVED_2 	= 1 << 7;

    private final BroadcastReceiver usb_receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean permission_granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                if (permission_granted) {
                    // permission given to the Vive tracker; do additional setup if necessary
                } else {
                    vive_tracker_connection = null;
                    htc_vive_tracker = null;
                }
            } else if (intent.getAction().equals(ACTION_USB_ATTACHED)) {
                checkForViveTracker();
                if (htc_vive_tracker != null) {
                    if (!connectToViveTracker()){
                        debugLog("Failed to connect to Vive tracker");
                    }
                }
            } else if (intent.getAction().equals(ACTION_USB_DETACHED)) {
                cleanUp();
            }
        }
    };

    private Runnable send_reset_packet_runnable = new Runnable() {
        @Override
        public void run() {
            sendResetPacket();
        }
    };

    /**
     * Sends a B4 reset packet to the Vive tracker. A reset packet is all the inputs zeroed out.
     * @return boolean      true, if the B4 packet was sent to the tracker; false, otherwise
     */
    private boolean sendResetPacket() {
        if (htc_vive_tracker != null &&
                vive_tracker_connection != null) {
            byte[] B4_data = {
                    (byte) 180,              // 0xB4; type: button inputs
                    10,                      // # of incoming bytes
                    0x00,                    // tag index
                    0x00,                    // reset inputs
                    0x00,                    // Touch pad X value LSB; sums up to [-32768, 32768]
                    0x00,                    // Touch pad X value MSB;
                    0x00,                    // touch pad y value LSB; sums up to [-32768, 32768]
                    0x00,                    // touch pad y value MSB;
                    0x00,                    // analog trigger LSB; sums up to [0, 65535]
                    0x00,                    // Analog trigger MSB; sums up to [0, 65535]
                    0x00,                    // reserved for battery
                    0x00,                    // reserved for battery
            };

            UsbInterface write_interface = htc_vive_tracker.getInterface(2);
            vive_tracker_connection.claimInterface(write_interface, true);

            int result = vive_tracker_connection.controlTransfer(0x21, 0x09, 0x0300, 2, B4_data, B4_data.length, 0);
            vive_tracker_connection.releaseInterface(write_interface);

            debugLog("Sent " + result + " bytes to vive tracker.\n");

            return (result >= 0) ? true : false;
        } else {
            return false;
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vive_tracker);
        device_info_tv = (TextView) findViewById(R.id.text_view);

        checkForViveTracker();
        if (htc_vive_tracker != null) {
            if (!connectToViveTracker()){
                debugLog("Failed to connect to Vive tracker");
            }
        }

        setFiltersForViveTracker();
    }

    /**
     * Release the USB connection to the Vive tracker for reuse.
     */
    private void cleanUp() {
        htc_vive_tracker = null;
        vive_tracker_connection = null;
        device_info_tv.setText("Found 0 usb devices.\n");
    }

    /**
     * Sets the intent filter for the Vive tracker over USB.
     */
    private void setFiltersForViveTracker() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_USB_PERMISSION);
        intentFilter.addAction(ACTION_USB_ATTACHED);
        intentFilter.addAction(ACTION_USB_DETACHED);

        registerReceiver(usb_receiver, intentFilter);
    }

    /**
     * Obtain detailed general information about a USB device.
     * @param device    The USB device to get information about
     * @return String   Nicely formatted String of the USB device information
     */
    private String deviceInfo(UsbDevice device) {
        return  "Manufacturer name: " + device.getManufacturerName() + "\n" +
                "Device name: " + device.getDeviceName() + "\n" +
                "Product name: " + device.getProductName() + "\n" +
                "Vendor ID: " + device.getVendorId() + "\n" +
                "Product ID: " + device.getProductId() + "\n" +
                "Device ID: " + device.getDeviceId() + "\n" +
                "Device Protocol: " + device.getDeviceProtocol() + "\n" +
                "Device Class: " + device.getDeviceClass() + "\n" +
                "Device Subclass: " + device.getDeviceSubclass() + "\n" +
                "# of Interfaces: " + device.getInterfaceCount() +
                getInterfaceInfo(device) + "\n";
    }

    /**
     * Obtain detailed information of the USB device's interface.
     * @param device    The USB device
     * @return String   Nicely formatted String of the USB device information
     */
    private String getInterfaceInfo(UsbDevice device) {
        int device_interface_count = device.getInterfaceCount();
        String interface_info = "";

        for (int i = 0; i < device_interface_count; i++) {
            UsbInterface device_interface = device.getInterface(i);
            interface_info = interface_info + "\nInterface #" + i + "\t" + device_interface.getName() + "\n" +
                    "\t# endpoints: " + device_interface.getEndpointCount() + "\n" +
                    "\tInterface class: " + device_interface.getInterfaceClass() + "\n" +
                    "\tInterface Protocol: " + device_interface.getInterfaceProtocol() + "\n" +
                    "\tInterface Subclass: " + device_interface.getInterfaceSubclass();
            for (int j = 0; j < device_interface.getEndpointCount(); j++) {
                interface_info = interface_info + getEndpointInfo(device_interface.getEndpoint(j));
            }
        }

        return interface_info;
    }

    /**
     * Obtain detailed information of the USB device's endpoints from the Usb interface.
     * @param device_interface  The USB interface of the connected USB device
     * @return String           Nicely formatted String of the USB interface information
     */
    private String getEndpointsInfo(UsbInterface device_interface) {
        int ep_count = device_interface.getEndpointCount();
        String ep_info = "";
        for (int i = 0; i < ep_count; i++) {
            UsbEndpoint usbEndpoint = device_interface.getEndpoint(i);
            ep_info = ep_info + "\n\t\tEndpoint #" + i + "\t" +
                    "\t\tDirection: " + usbEndpoint.getDirection() + "\n" +
                    "\t\tType: " + usbEndpoint.getType() + "\n" +
                    "\t\tMax Packet Size supported: " + usbEndpoint.getMaxPacketSize() + "\n";
        }

        return ep_info;
    }

    /**
     * Obtain detailed information of the USB device's endpoint.
     * @param usbEndpoint   The USB endpoint of the USB device
     * @return              Nicely formatted String of the USB endpoint info
     */
    private String getEndpointInfo(UsbEndpoint usbEndpoint) {
        String ep_info = "";

        ep_info = ep_info + "\n\t\tEndpoint #" + usbEndpoint.getEndpointNumber() + "\n" +
                "\t\tDirection: " + usbEndpoint.getDirection() + "\n" +
                "\t\tType: " + usbEndpoint.getType() + "\n" +
                "\t\tMax Packet Size supported: " + usbEndpoint.getMaxPacketSize() + "\n";

        return ep_info;
    }

    /**
     * Check if the Vive tracker is connected to the host device.
     */
    private void checkForViveTracker() {
        UsbManager usb_manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> device_list = usb_manager.getDeviceList();
        Iterator<UsbDevice> device_list_ptr = device_list.values().iterator();
        device_info_tv.setText("Found " + device_list.size() + " usb devices.\n");
        while (device_list_ptr.hasNext()) {
            UsbDevice device = device_list_ptr.next();

            String device_info = deviceInfo(device);

            device_info_tv.append(device_info);

            if (device.getProductName().toLowerCase().equals("vrc")) {
                debugLog("Found Vive tracker");
                htc_vive_tracker = device;
            }
        }
    }

    /**
     * Check if the Vive tracker is connected to the host device. If it is try to establish
     * connection to it through USB. Note: this method is invoke when you press the search
     * for vive tracker button of the UI
     * @param view      The button view
     */
    public void checkForViveTrackerAndConnect(View view) {
        UsbManager usb_manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> device_list = usb_manager.getDeviceList();
        Iterator<UsbDevice> device_list_ptr = device_list.values().iterator();
        device_info_tv.setText("Found " + device_list.size() + " usb devices.\n");
        while (device_list_ptr.hasNext()) {
            UsbDevice device = device_list_ptr.next();

            if (device.getProductName().toLowerCase().equals("vrc")) {
                debugLog("Found vive tracker");
                String device_info = deviceInfo(device);
                device_info_tv.append(device_info);
                htc_vive_tracker = device;
                connectToViveTracker();
            }
        }
    }

    /**
     * Connects to the Vive tracker.
     * @return boolean      true, if successfully connected to the Vive tracker; false, otherwise
     */
    private boolean connectToViveTracker() {

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        usbManager.requestPermission(htc_vive_tracker, pendingIntent);

        if (vive_tracker_connection == null) {
            vive_tracker_connection = usbManager.openDevice(htc_vive_tracker);

            boolean connection_status = (vive_tracker_connection == null) ? false : true;

            // immediately after connection, send a report packet to maintain tracking
            boolean report_feature_status = sendB3Packet();
            if (connection_status && report_feature_status) {
                debugLog("Successfully connected to Vive tracker");
                return true;
            } else {
                debugLog("Failed to connect to Vive tracker");
                if (!report_feature_status) {
                    debugLog("Failed to send init B3 Report Feature\n");
                }
                return false;
            }
        } else {
            debugLog("Vive tracker is already connected");
            return true;
        }
    }

    /**
     * Sends a B3 (status) packet to the Vive tracker over USB.
     * @return boolean      true, if successfully sent; false, otherwise
     */
    private boolean sendB3Packet() {
        if (htc_vive_tracker != null &&
                vive_tracker_connection != null) {
            byte[] B3_data = {
                    (byte) 179,     // 0xB3
                    3,              // # of incoming bytes
                    3,              // Host type: Accessory
                    0,              // charge enabled; RESERVED
                    0,              // OS type; RESERVED
            };

            UsbInterface write_interface = htc_vive_tracker.getInterface(2);

            vive_tracker_connection.claimInterface(write_interface, true);

            int result = vive_tracker_connection.controlTransfer(0x21, 0x09, 0x0300, 2, B3_data, B3_data.length, 0);
            vive_tracker_connection.releaseInterface(write_interface);

            debugLog("Sent " + result + " bytes to vive tracker.\n");

            return (result >= 0) ? true : false;
        } else {
            return false;
        }
    }

    /**
     * Send a B4 (buttons pressed) packet to the Vive tracker over USB.
     * @param type      The type of button pressed (e.g. touch pad, trigger, etc).
     * @return boolean  true, if the button pressed packet successfully sent; false, otherwise
     */
    private boolean sendB4Packet(int type) {
        switch (type) {
            case TRACKER_PAD_PRESS:
                debugLog("Sending Touch Pad Down...");
                return sendTouchPadDownPkt();
            case TRACKER_TRIGGER:
                debugLog("Sending Trigger Press...");
                return sendTriggerPressPkt();
            default:
                break;
        }

        return false;
    }

    /**
     * Sends a touch pad down B4 packet to the Vive tracker. Note: This method is invoked
     * when the UI button send touch pad down is pressed.
     * @param view  The UI button view
     */
    public void sendTouchPadDownPress(View view) {
        sendTouchPadTouch();
    }

    /**
     * Delay for a short period of time for the Vive tracker to process data.
     */
    private void delayForViveTracker() {
        // TODO: change pause to use handler
        for (int i = 0; i < 10000000; i++) {
            // busy wait;
        }
    }

    /**
     * Sends a touch pad down packet to the Vive tracker.
     */
    private void sendTouchPadTouch() {
        debugLog("Sending B3 Packet");
        boolean success_b3 = sendB3Packet();
        if (success_b3) {
            debugLog("B3 Packet successful send");
        } else {
            debugLog("B3 Packet Failed to send");
        }

        // the vive tracker requires a small delay after receiving a B3 packet
        delayForViveTracker();

        debugLog("Sending B4 Packet");
        boolean success_b4 = sendB4Packet(TRACKER_PAD_PRESS);

        if (success_b4) {
            debugLog("B4 packet successful send");
        } else {
            debugLog("B4 packet failed to send");
        }
    }

    /**
     * Sends a trigger press to the Vive tracker.
     */
    private void sendTriggerPressSequence() {
        debugLog("Sending B3 Packet");
        boolean success_b3 = sendB3Packet();

        if (success_b3) {
            debugLog("B3 Packet successful send");
        } else {
            debugLog("B3 Packet Failed to send");
        }

        // the vive tracker requires a small delay after receiving a B3 packet
        delayForViveTracker();

        debugLog("Sending B4 Packet");
        boolean success_b4 = sendB4Packet(TRACKER_TRIGGER);

        if (success_b4) {
            debugLog("B4 packet successful send");
        } else {
            debugLog("B4 packet failed to send");
        }
    }

    /**
     * Low level of sending a trigger press to the Vive tracker.
     * @return boolean      true, if successfully sent; false, otherwise
     */
    public boolean sendTriggerPressPkt() {
        if (htc_vive_tracker != null &&
                vive_tracker_connection != null) {
            byte[] B4_data = {
                    (byte) 180,         // 0xB4; type: button inputs
                    10,                 // # of incoming bytes
                    0x00,               // tag index
                    TRACKER_TRIGGER,  // track pad press
                    0x00,                    // Touch pad X value LSB; sums up to [-32768, 32768]
                    0x00,                    // Touch pad X value MSB;
                    0x00,                    // touch pad y value LSB; sums up to [-32768, 32768]
                    0x00,                    // touch pad y value MSB;
                    0x00,                    // analog trigger LSB; sums up to [0, 65535]
                    0x00,                    // Analog trigger MSB; sums up to [0, 65535]
                    0x00,                    // reserved for battery
                    0x00,                    // reserved for battery
            };

            UsbInterface write_interface = htc_vive_tracker.getInterface(2);
            vive_tracker_connection.claimInterface(write_interface, true);

            int result = vive_tracker_connection.controlTransfer(0x21, 0x09, 0x0300, 2, B4_data, B4_data.length, 0);
            vive_tracker_connection.releaseInterface(write_interface);

            vive_tracker_reset_handler.postDelayed(send_reset_packet_runnable, 2000);   // after 2 seconds stop reporting

            debugLog("Sent " + result + " bytes to vive tracker.\n");

            return (result >= 0) ? true : false;
        } else {
            return false;
        }
    }

    /**
     * Sends a trigger press to the Vive tracker. Note: this method is invoke when the UI button
     * send trigger press is pressed.
     * @param view      The UI view of the button
     */
    public void sendTriggerPress(View view) {
        sendTriggerPressSequence();
    }

    /**
     * Low level sending of a touch pad press.
     * @return boolean      true, if successfully sent; false, otherwise
     */
    private boolean sendTouchPadDownPkt() {
        if (htc_vive_tracker != null &&
                vive_tracker_connection != null) {
            byte[] B4_data = {
                    (byte) 180,         // 0xB4; type: button inputs
                    10,                 // # of incoming bytes
                    0x00,               // tag index
                    TRACKER_PAD_TOUCH | TRACKER_PAD_PRESS,  // track pad press
                    0x00,                    // Touch pad X value LSB; sums up to [-32768, 32768]
                    0x00,                    // Touch pad X value MSB;
                    0x00,                    // touch pad y value LSB; sums up to [-32768, 32768]
                    0x00,                    // touch pad y value MSB;
                    0x00,                    // analog trigger LSB; sums up to [0, 65535]
                    0x00,                    // Analog trigger MSB; sums up to [0, 65535]
                    0x00,                    // reserved for battery
                    0x00,                    // reserved for battery
            };

            UsbInterface write_interface = htc_vive_tracker.getInterface(2);
            vive_tracker_connection.claimInterface(write_interface, true);

            int result = vive_tracker_connection.controlTransfer(0x21, 0x09, 0x0300, 2, B4_data, B4_data.length, 0);
            vive_tracker_connection.releaseInterface(write_interface);

            vive_tracker_reset_handler.postDelayed(send_reset_packet_runnable, 2000);   // after 2 seconds stop reporting

            debugLog("Sent " + result + " bytes to vive tracker.\n");

            return (result >= 0) ? true : false;
        } else {
            return false;
        }
    }

    /**
     * Low level send touch pad contact to Vive tracker.
     * @return boolean      true, if the successfully sent; false, otherwise
     */
    private boolean sendTouchContactPkt() {
        if (htc_vive_tracker != null &&
                vive_tracker_connection != null) {
            byte[] B4_data = {
                    (byte) 180,         // 0xB4; type: button inputs
                    10,                 // # of incoming bytes
                    0x00,               // tag index
                    TRACKER_PAD_TOUCH,  // track pad touch
                    0x00,                    // Touch pad X value LSB; sums up to [-32768, 32768]
                    0x00,                    // Touch pad X value MSB;
                    0x00,                    // touch pad y value LSB; sums up to [-32768, 32768]
                    0x00,                    // touch pad y value MSB;
                    0x00,                    // analog trigger LSB; sums up to [0, 65535]
                    0x00,                    // Analog trigger MSB; sums up to [0, 65535]
                    0x00,                    // reserved for battery
                    0x00,                    // reserved for battery
            };

            UsbInterface write_interface = htc_vive_tracker.getInterface(2);

            vive_tracker_connection.claimInterface(write_interface, true);

            int result = vive_tracker_connection.controlTransfer(0x21, 0x09, 0x0300, 2, B4_data, B4_data.length, 0);
            vive_tracker_connection.releaseInterface(write_interface);

            debugLog("Sent " + result + " bytes to vive tracker.\n");

            return (result >= 0) ? true : false;
        } else {
            return false;
        }
    }

    /**
     * Sends a B3 status packet and then a B4 touch pad contact packet to the Vive tracker.
     */
    public void sendTouchContactSequence() {
        debugLog("Sending B3 Packet");
        boolean success_b3 = sendB3Packet();

        if (success_b3) {
            debugLog("B3 Packet successful send");
        } else {
            debugLog("B3 Packet Failed to send");
        }

        delayForViveTracker();

        debugLog("Sending B4 Packet");
        boolean success_b4 = sendTouchContactPkt();

        if (success_b4) {
            debugLog("B4 packet successful send");
        } else {
            debugLog("B4 packet failed to send");
        }
    }

    /**
     * Sends a touch pad contact (finger over touch pad) packet over to the Vive tracker.
     * Note: this method is invoked by the send touch contact UI button.
     * @param view      The view of the button
     */
    public void sendTouchContact(View view) {
        sendTouchContactSequence();
    }

    /**
     * Write the debugging message to the text view and logcat system.
     * @param msg
     */
    void debugLog(String msg) {
        Log.d("[ViveTrackerDemo]", msg);

        if (device_info_tv != null) {
            device_info_tv.append(msg + "\n");
        }
    }
}
