/**
 * This Sketch reads de LDR value and sends it to the Java Program
 * via Serial Port: Arduino->Serial->PC->Java
 *
 * LDR (Light Dependent Resistor):
 *  [0 (dark/500k ohm).. 1000 (bright)]
 */
#define PINA0 A0
int light = 0;

void setup()
{
    Serial.begin(9600);
}

void loop()
{
    //Read value from sensor: 0 = no light; 1000 = max light (10 lux)
    //light = analogRead(PINA0);
    if (light<500) light=750; else light=250;
    //Send message: light=750;
    Serial.print("light=");
    Serial.print(light);
    Serial.println(";");
    delay(2000);
}