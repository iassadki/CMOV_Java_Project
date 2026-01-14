package edu.ufp.cm.arduinoserialmonitor.usingoldrxtxlib;

import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import edu.ufp.cm.arduinoserialmonitor.BulbLamp;
import gnu.io.*; // for rxtxSerial library
//import javax.comm.*; // for SUN's serial/parallel port libraries


/**
 * How To Install libraries to use project:
 *
 * 1. Download JARs and RxTx library from:
 *  - MAC platform <http://jlog.org/rxtx-mac.html>
 *    Please download 'RXTXcomm.jar' and 'librxtxSerial.jnilib' and
 *    install them in the on the
 *      '/Library/Java/Extensions' folder to make them available to all users 
 *    or
 *      '¬/Library/Java/Extensions' to be available for the current user only.
 *
 *  - WIN platform <http://rxtx.qbang.org/wiki/index.php/Installation_for_Windows>
 *    Download rxtxParallel.dll + rxtxSerial.dll and copy them to 
 *      C:\Program Files\Java\jdk1.6.0_01\bin\
 *    Download + RXTXcomm.jar and copy them to
 *      C:\Program Files\Java\jdk1.6.0_01\lib\ext\ or \lib on Java project
 *
 * 2. Install library under JRE:
 *  - copy file rxtxSerial.dll to %JDK%\jre\bin\rxtxSerial.dll;
 * 
 * 3. Install/Copy JARs under Java Project:
 *  - CM>lib
 *  - CM Project > Properties: BuildPath > Libraries > AddExternalJARs:
 *    select all files from CM\lib\: RXTXcomm.jar + comm.jar + jakarta-oro.jar
 */
public class ArduinoSerialMonitorRxTx implements WindowListener, ActionListener, Runnable, SerialPortEventListener {

    private static final ArrayList<CommPortIdentifier> vectorSerialPortID = new ArrayList<>();
    private static Enumeration portList;
    //private static CommPortIdentifier portID;
    private SerialPort serialPort;
    private Thread readThread;
    
    private InputStream inputStream;    
    private InputStreamReader is;
    private BufferedReader br;
    
    private OutputStream outputStream;
    private PrintWriter pw;
    private final boolean outputBufferEmptyFlag = false;

    //Inicializar as variaveis para das interfaces
    private final JFrame jframe = new JFrame("Shinning");
    private final JComboBox<String> jcbox = new JComboBox<>();
    private final JLabel jlabel = new JLabel("Ports: ");
    private final Container c = jframe.getContentPane();
    //private BulbLamp lamp = new BulbLamp("LampON.jpg", "LampOFF.jpg");
    private final BulbLamp lamp = new BulbLamp("res/HandLampOk.jpg", "res/HandLamp.jpg");

    public static void main(String[] args) {
        ArrayList<String> vectorSerialPorts = new ArrayList<>();
        boolean portFound = false;
        String defaultPort = "COM11";

        System.out.println("ArduinoSerialMonitor - main(): set default port to " + defaultPort);

        // Find available ports
        portList = CommPortIdentifier.getPortIdentifiers();

        while (portList.hasMoreElements()) {
            CommPortIdentifier port = (CommPortIdentifier) portList.nextElement();
            if (port.getPortType() == CommPortIdentifier.PORT_SERIAL) {
                //System.out.println("ArduinoSerialMonitor - main(): port found: " + port.getName());
                vectorSerialPortID.add(port);
                vectorSerialPorts.add(port.getName());
            }

            /*
            if (port.getPortType() == CommPortIdentifier.PORT_SERIAL) {
            portID = port;
            System.out.println("ArduinoSerialMonitor - main(): port found: " + portID.getName());
            portFound = true;
            }
             */
        }
        if (vectorSerialPorts.size() > 0) {
            ArduinoSerialMonitorRxTx monitor = new ArduinoSerialMonitorRxTx(vectorSerialPorts);
        }
    }

    //Construtor 
    public ArduinoSerialMonitorRxTx(ArrayList<String> vectorSerialPorts) {
        System.out.println("ArduinoSerialMonitor - ArduinoSerialMonitor(): ports found = " + vectorSerialPorts.size());

        //jcbox = new JComboBox();
        //jcbox.setSelectedIndex(jcbox.getItemCount()-1);
        jcbox.removeAllItems();
        vectorSerialPorts.add(0, "Select COM port...");
        for (String s : vectorSerialPorts) {
            if (s!=null && jcbox!=null){
                jcbox.addItem(s);
            }
        }

        c.add(BorderLayout.NORTH, jcbox);
        c.add(BorderLayout.CENTER, lamp);
        c.add(BorderLayout.SOUTH, jlabel);

        jcbox.addActionListener(this);
        jframe.addWindowListener(this);

        jframe.pack();
        jframe.setSize(400, 540);
        //int w = jcbox.getWidth();
        //int h = jcbox.getHeight() + lamp.getHeight() + jlabel.getHeight();
        //System.out.println("ArduinoSerialMonitor - ArduinoSerialMonitor(): width x hight = " + w + ", " + h);
        //jframe.setSize(w, h);
        jframe.setVisible(true);
        jframe.repaint();
    }

    @Override
    public void run() {
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        //throw new UnsupportedOperationException("Not supported yet.");
        JComboBox cb = (JComboBox) e.getSource();
        String port = (String) cb.getSelectedItem();
        int item = cb.getSelectedIndex();
        System.out.println("ArduinoSerialMonitor - actionPerformed(): port combo box selected item = " + item);

        if (item > 0) {
            // Get SerialPort
            CommPortIdentifier portid = vectorSerialPortID.get(item - 1);
            System.out.println("ArduinoSerialMonitor - actionPerformed(): item = " + item + " portid = " + portid.getName());
            // Inicializa a porta serie
            try {
                serialPort = (SerialPort) portid.open("SimpleRead", 2000);

                // Set reader
                inputStream = serialPort.getInputStream();
                is = new InputStreamReader(this.inputStream);
                br = new BufferedReader(is);
                
                // Set writer
                outputStream = serialPort.getOutputStream();
                pw = new PrintWriter(outputStream);
                
                // Activate DATA_AVAILABLE notifier
                serialPort.notifyOnDataAvailable(true);
                // Set serial port parameters
                serialPort.setSerialPortParams(9600, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);

                // Handle events from serial port
                serialPort.addEventListener(this);

                // Inicia a leitura da thread
                //readThread = new Thread(this);
                //readThread.start();
            } catch (PortInUseException piue) {
                piue.printStackTrace();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            } catch (TooManyListenersException tmle) {
                tmle.printStackTrace();
            } catch (UnsupportedCommOperationException ucoe) {
                ucoe.printStackTrace();
            }
        }
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
        switch (event.getEventType()) {
            case SerialPortEvent.BI:
            case SerialPortEvent.OE:
            case SerialPortEvent.FE:
            case SerialPortEvent.PE:
            case SerialPortEvent.CD:
            case SerialPortEvent.CTS:
            case SerialPortEvent.DSR:
            case SerialPortEvent.RI:
            case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
                break;
            case SerialPortEvent.DATA_AVAILABLE:
                // Colocar no readBuffer a informacao recebida
                try {
                    //InputStreamReader is= new InputStreamReader(this.inputStream);
                    //BufferedReader br = new BufferedReader(is);
                    String result = br.readLine();
                    System.out.println("serialEvent(" + SerialPortEvent.DATA_AVAILABLE + "): result = " + result);

                    //Clear tail spaces
                    result = result.trim();
                    
                    //Parse Light Sensor value sent via serial port,
                    //message is something like: Sensor = 800
                    parseLightSensorInput(result);
                                        
                    this.jframe.repaint();

                } catch (IOException e) {
                    //e.printStackTrace();
                    System.out.println(e.toString());
                }
                break;
        }
    }
    
    private void parseLightSensorInput(String result) {
        //Parse Light Sensor value sent via serial port,
        //assuming message is something like: "Light=800"
        //We need to extract the number out of the string
        int eqIndex = result.indexOf("=")+1;

        System.out.println("parseLightSensorInput(): eqIndex = " + eqIndex);

        String sensorValueStr = result.substring(eqIndex);
        System.out.println("parseLightSensorInput(): sensorValueStr = " + sensorValueStr);
        
        int sensorValue = Integer.parseInt(sensorValueStr);
        System.out.println("parseLightSensorInput(): sensorValue = " + sensorValue);

        if (sensorValue<500) {
            jlabel.setText("result = " + result);
            jlabel.setForeground(Color.BLACK);
            lamp.lampON();
            //lamp.repaint();
        } else {
            jlabel.setText("result = " + result);
            jlabel.setForeground(Color.BLACK);
            lamp.lampOFF();
            //lamp.repaint();
        }
    }
    
    private void parseHorLInput(String msg) {
        /*
        // Leitura do comando H / L enviado pelo Arduino
        int charIndex = -1;
        if (charIndex == -1) {
            charIndex = result.indexOf('H');
        }
        if (charIndex == -1) {
            charIndex = result.indexOf('L');
        }

        System.out.println("serialEvent(" + SerialPortEvent.DATA_AVAILABLE + "): charIndex = " + charIndex);
        char c = result.charAt(charIndex);
        System.out.println("serialEvent(" + SerialPortEvent.DATA_AVAILABLE + "): c = " + c);

        
        if (c == 'H' || c == 'h') {
            jlabel.setText("result = " + result);
            jlabel.setForeground(Color.BLACK);
            lamp.lampON();
            //lamp.repaint();
        } else if (c == 'L' || c == 'L') {
            jlabel.setText("result = " + result);
            jlabel.setForeground(Color.BLACK);
            lamp.lampOFF();
            //lamp.repaint();
        }
        */
    }
    
    public void writeToSerialPort(String msg) {
        System.out.println("ArduinoSerialMonitor - writeToSerialPort(\"" + msg + "\"): to " + serialPort.getName());
        try {
            // write string to serial port
            outputStream.write(msg.getBytes());
            System.out.println("ArduinoSerialMonitor - writeToSerialPort(\"" + msg + "\"): finished sending to " + serialPort.getName());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String readFromSerialPort() {
        System.out.println("ArduinoSerialMonitor - readFromSerialPort(): from " + serialPort.getName());
        String comMsg = null;
        try {
            // Input porta serie
            InputStreamReader isr = new InputStreamReader(this.inputStream);
            BufferedReader braux = new BufferedReader(isr);
            comMsg = braux.readLine();
            br.close();
            System.out.println("ArduinoSerialMonitor - readFromSerialPort(): comMsg " + comMsg);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return comMsg;
    }

    @Override
    public void windowOpened(WindowEvent e) {
        //throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void windowClosing(WindowEvent e) {
        //throw new UnsupportedOperationException("Not supported yet.");
        this.jframe.dispose();
        System.exit(0);
    }

    @Override
    public void windowClosed(WindowEvent e) {
        this.jframe.dispose();
        System.exit(0);
    }

    @Override
    public void windowIconified(WindowEvent e) {
        //throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void windowDeiconified(WindowEvent e) {
        //throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void windowActivated(WindowEvent e) {
        //throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void windowDeactivated(WindowEvent e) {
        //throw new UnsupportedOperationException("Not supported yet.");
    }
}
