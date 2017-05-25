# HeartBEAT

HeartBEAT is an application that plays music from your personal Spotify playlist with the same beats per minute (BPM) as your heart rate (HR).
This could be useful while exercising, at work, driving in your car, or plenty of other applications!

Your heart rate is measured with sensors attached to an mbed device, and then sent over BLE to an Android application. 
The Android aplication compares your heart rate to your Spotify music and plays the corresponding songs.

## Requirements

In order to run HeartBEAT, you need the following:
* Android phone running at *least* Android 6.0 (M)
* mbed device capable of BLE or a [BLE component](https://developer.mbed.org/components/cat/bluetooth/) attached to non-BLE board. This example uses the [NUCLEO-IDB05A1](https://developer.mbed.org/components/X-NUCLEO-IDB05A1-Bluetooth-Low-Energy/)
* [Grove heart rate monitor](https://www.seeedstudio.com/Grove-Ear-clip-Heart-Rate-Sensor-p-1116.html)
* [Grove base shield](https://developer.mbed.org/components/Seeed-Grove-Shield-V2/)
* Spotify premium account with access to the [developer portal](https://developer.spotify.com/)

## Installation

Clone the repository onto your computer. Make sure `mbed CLI` is installed with the requirements as shown in this [video](https://www.youtube.com/watch?v=PI1Kq9RSN_Y&t=2s).
Additionally, make sure Android studio is installed properly as described in this [guide](https://developer.android.com/studio/index.html).

### Client setup

Make sure everything is hooked up properly. You need to have the following:
* BLE shield is connected to your target (omit this if your board has built in BLE). *NOTE: I used the NUCLEO_F401RE with IDB05A1 which required a [few patches](https://developer.mbed.org/teams/ST/code/X_NUCLEO_IDB0XA1/)*
* Grove base shield is connected to your target
* Grove heart rate sensor is connected to port D2. This can be changed if necessary in `main.cpp` by changing the `InterruptIn` pinout

Follow the following steps:
1. Enter the `Client` folder
2. Run `mbed deploy` to update all dependencies
3. Compile and download the binary to your device (`mbed compile -t GCC_ARM -m NUCLEO_F401RE`)

No changes should be required for the client code. If you wish, you can change the BLE device name by altering the `DEVICE_NAME` variable. 
Looking into `main.cpp`, you can see how the BLE library is used to connect to the Android application, as well as setup a few BLE characteristics for storing the device name, and the latest heart rate.

### Android setup

In Android studio, open up the HeartBEAT folder and clean the project. There are a few setup steps you must follow before you can download the app onto your phone.

First, you must have a developer account with spotify, which requires you to have a Spotify premium account. If you have a premium account, go to the [developer panel](https://developer.spotify.com/my-applications/#!/) and register. 

Once logged in, create a new application by clicking on the `Create an App` button. Fill out the corresponding details, including adding your Android developer SHA, which you can find by running the following command in Android Studio Console: 
`keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`

After you have created an app in the Spotify developer portal, you can find your client ID and secret key by selecting on your app and looking for the `Client ID` and `Client Secret Key` fields. Copy the client ID as you will need it later.

Open `Home.java` in Android studio. Change the `Client ID` to your client ID you found in the step above. The line of code should look like this:
```java
// Spotify ID
// @TODO replace the client ID with your personal Spotify client ID
private static final String CLIENT_ID = "123456789012345678901234567890";
```

Compile your code, and deploy to your device.

## Running

Reset the client and attach the Grove heart rate sensor to your ear. After a few seconds, your heart rate will start sending over BLE.

Open the Android application on your phone and click the `Connect` button in the middle of the screen. This should bring up a dialog box as shown below. Click the HeartBEAT list item to connect.

<img src="https://github.com/mray19027/HeartBEAT/blob/master/imgs/connect.png" width="300">

After a few seconds, the Android app will connect to the device and heart rate information should display on the screen as shown below.

<img src="https://github.com/mray19027/HeartBEAT/blob/master/imgs/home.png" width="300">

Clicking play will start the song corresponding to your heart rate. Any time the song ends or you click `Next`, your current heart rate will be used to select the next song.
