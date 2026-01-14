/**
 * Support for median statistical calculation.
 */

//Headers
int compare_two_values(const void *value1, const void *value2);
int calc_median(int values[], int size);
int collect_readings_median(int analoguePort, int numReadings);

//Function to compare two values and returns 0 when value1==value2.
int compare_two_values(const void *value1, const void *value2)
{
  int a = *((int *)value1);
  int b = *((int *)value2);
  //return ( (a>b) ? -1 : ( (a<b) ? 1 : 0) );
  return (a-b);
}

int calc_median(int values[], int size)
{
    //Sort array of values... requires the use of a comparison function such as compare_two_values()
    qsort(values, size, sizeof(int), compare_two_values);
    //Return median value (value stored in the middle of array)
    return ( (size%2 != 0) ? values[size/2] : (values[size/2-1]+values[size/2])/2 );
}

int collect_readings_median(int analoguePort, int numReadings)
{
    // Use several readings (spaced 10ms)
    int analog_readings[numReadings];
    for(int i=0; i<numReadings; i++)
    {
      analog_readings[i] = analogRead(analoguePort);
      delay(10);
    }
    //Then get median (statistically avoid outlier readings)
    return calc_median(analog_readings, numReadings);
}

