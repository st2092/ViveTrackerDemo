# Vive Tracker Demo
This Android app demonstrates basic functionalities with the Vive tracker. The app can find and connect with the Vive tracker through USB. The app can also send button presses to the Vive tracker. 

All the button inputs sent through the Android host device to the Vive tracker will reflect on a machine running SteamVR. For example, if a machine has Unreal Engine 4 or Unity running and has the Vive tracker paired to it, when the Android device running this app connects to the tracker button inputs from the app will reflect in Unreal Engine 4 or Unity.

The purpose of this app is to show that it is possible to use the advanced USB features of the Vive tracker without having to dive into embedded systems.

## How the App Works
Connect your Android host device (usually smartphone) with the app installed to the Vive tracker. Open the app and tap on the send button command buttons in the app. The machine running steamVR and is connected to the tracker will pick up those inputs.

## Detailed Steps to Using the App
1. Start steamVR and pair the Vive tracker on machine
2. Connect the Android host device to the Vive tracker via USB
3. Open the app
4. The app will attempt to search for the Vive tracker. If it fails to find the Vive tracker try again with the search for device button.
5. When the Vive tracker do get pick up by the Android system you will see the details of the Vive tracker on the screen.
![Vive Tracker Connect](/img/vive-tracker-connected.png)
6. Press the send trigger press, touch pad down press, or touch contact button to send the data over to the Vive tracker.

## Important Notes
* When the Vive tracker connects with the Android host-device it will lose tracking unless the Android device immediately sends a B3 status packet.
* After sending a button press to the Vive tracker it will repeatedly report that button press to steamVR. So, for every button press sent on the Android end you must follow up with a zeroed-out button press data packet.
* Development of this app uses a developer version of the tracker. The consumer version may have some attributes that are different, which may prevent the app from functioning properly.