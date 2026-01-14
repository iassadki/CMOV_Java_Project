/**
 * Lab description:
 *  Use one Analogic port (with a 10 bit ADC) to read an analog input signal (cf. LDR) between [0..1000].
 *  Map the luminosity into a value between [0..255].
 *  Send the light sensor value via Serial/XBee to be received on XBee Coordinator connected to PC.
 *  The message MUST have the following format:
 *      light=180 (this is the message format expected (to be parsed) by the Java program running on PC).
 *
 * Assembly:
 *  This Lab assumes the use of
 *    - Arduino coupled to XBee Shield + XBee End module and LDR sensor connected to analog port (please check previous *ldr_sensor* lab).
 *    - XBee Coordinator coupled to XBee Explorer will be plugged (via serial cable) to PC running Java app.
 *
 * Additional Libraries and software:
 *  Install IntelliJ IDE: <https://www.jetbrains.com/idea/download/>
 *    NB: must register on the JetBrain site with institutional UFP email to get a student license.
 *
 *  Copy CM.zip to directory Documents/JavaProjects in the PC, then unzip and open CM project with IntelliJ installed earlier;
 *    NB: CM.zip project is available in the Lab folder.
 *
 *  Install RxTx library, following instruction in the CM project:
 *    NB: open the ArduinoSerialMonitorRxTx.java Java class file (available on the left side of IntelliJ) and follow the instructions in header comment, to install the RxTx library, needed to communicate with the USB port and run the Lab.
 *
 * Challenge:
 *  If you have Java programming skills, try to use a MQTT Java library to interact with a Cloud IoT service (e.g. ThingSpeak, IFTTT) to send light values received in the Java app, and possibly automate simple actions.
 *
 */
//#include <statistical_support_analog_readings.h> //Includes .h file from directory Documents/Arduino/Libraries/statistical_support_analog_readings
#include "statistical_support_analog_readings.h" ////Includes .h file from current directory 
#define MAX_READINGS 5
#define PIN_LDR A0

int lightValue = 100;

void setup() 
{ 
  //Set baud rate
  Serial.begin(9600);
} 

void loop() 
{ 
	// Read lightValue using a 10 bits ADC (value between 0 and 1023)
  // Execute several readings and return the median to avoid outliers
  //int lightValue = collect_readings_median(PIN_LDR, MAX_READINGS);

  //Simulate reading increasing light values from 200 to 700
  lightValue += 200; 

  if (lightValue>700){ //When getting to 700 return back to 100
        lightValue = 200;
  }

  //Send message through Serial/XBee to PC running ArduinoSerialMonitorJSerialComm Java program
  //Message MUST have the following format: 
  //  "light=750;", where 750 is the luminosity value from LDR
  // The luminosity must be between '=' and ';' chars
	Serial.print("light="); //The char '=' must preceed the luminosity value/number
	Serial.print(lightValue);
  Serial.println(";"); //The char ';' must follow the luminosity value/number
  //Serial.flush();
	//Wait...
	delay(2000);
}
