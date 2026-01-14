package edu.ufp.cm.arduinoserialmonitor;

import java.awt.*;
import javax.swing.JPanel;

public class BulbLamp extends JPanel {

    private Image imgBulbOff = null;
    private Image imgBulbOn = null;
    private boolean on = false;
    
//    public BulbLamp() {
//       imgBulbOff = Toolkit.getDefaultToolkit().createImage("LampOFF.jpg");
//       imgBulbOn = Toolkit.getDefaultToolkit().createImage("LampON.jpg");
//       Toolkit.getDefaultToolkit().checkImage(imgBulbOff, imgBulbOff.getWidth(this), imgBulbOff.getHeight(this), this);
//       Toolkit.getDefaultToolkit().checkImage(imgBulbOff, imgBulbOff.getWidth(this), imgBulbOff.getHeight(this), this);
//       System.out.println("BulbLamp(): imgBulbOff width x hight = "+imgBulbOff.getWidth(this)+", "+imgBulbOff.getHeight(this));
//       //this.setSize(imgBulbOff.getWidth(this)+20, imgBulbOff.getHeight(this)+20);
//    }

    public BulbLamp(String onFile, String offFile) {
       imgBulbOff = Toolkit.getDefaultToolkit().createImage(offFile);
       imgBulbOn = Toolkit.getDefaultToolkit().createImage(onFile);
       Toolkit.getDefaultToolkit().checkImage(imgBulbOff, imgBulbOff.getWidth(this), imgBulbOff.getHeight(this), this);
       Toolkit.getDefaultToolkit().checkImage(imgBulbOff, imgBulbOff.getWidth(this), imgBulbOff.getHeight(this), this);
       System.out.println("BulbLamp(): imgBulbOff width x hight = "+imgBulbOff.getWidth(this)+", "+imgBulbOff.getHeight(this));
       //this.setSize(imgBulbOff.getWidth(this)+20, imgBulbOff.getHeight(this)+20);
    }

    public void lampON() {
        //imgBulbOff = Toolkit.getDefaultToolkit().getImage("LampON.jpg");
        System.out.println("BulbLamp - lampON(): width & hight = "+imgBulbOff.getWidth(this)+", "+imgBulbOff.getHeight(this));
        on = true;
    }

    public void lampOFF() {
        //imgBulbOff = Toolkit.getDefaultToolkit().getImage("LampOFF.jpg");
        System.out.println("BulbLamp - lampOFF(): width & hight = "+imgBulbOff.getWidth(this)+", "+imgBulbOff.getHeight(this));
        on = false;
    }

    public void update(Graphics g) {
        paint(g);
    }

    public void paint(Graphics g) {
        if (imgBulbOff != null && imgBulbOn != null) {
            if (on) g.drawImage(imgBulbOn, 10, 10, this);
            else g.drawImage(imgBulbOff, 10, 10, this);
        } else {
            g.clearRect(0, 0, getSize().width, getSize().height);
        }
    }
}