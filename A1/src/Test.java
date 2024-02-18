import java.awt.image.*;
import java.io.*;
import javax.imageio.*;

// my imports
import java.awt.Color;
import java.lang.System;

public class Test {

  // The image constructed
  public static BufferedImage img;

  // Image dimensions; you can modify these for bigger/smaller images
  public static int width = 1920;
  public static int height = 1080;

  // command-line args
  public static int r; // max circle radius
  public static int c; // number of circles to draw
  public static boolean multithreaded; // if true then two threads else one thread

  // use these if multithreaded, else draw one circle at a time
  public static Circle circle1;
  public static Circle circle2;


  /**
   * convenience methods
   */

  public static boolean circlesOverlap(Circle c1, Circle c2) {
    for (int x = c1.x - width; x <= c1.x + width; x += width) {
      for (int y = c1.y - height; y <= c1.y + height; y += height) {
        int x_squared = (x - c2.x) * (x - c2.x);
        int y_squared = (y - c2.y) * (y - c2.y);
        int r_squared = (c1.r + c2.r) * (c1.r + c2.r);
        if (x_squared + y_squared < r_squared) {
          return true;
        }
      }
    }
    return false;
  }

  public static void drawCircle(Circle c, int rgb) {
    for (int i = c.x - c.r; i <= c.x + c.r; i++) {
      for (int j = c.y - c.r; j <= c.y + c.r; j++) {
        int x = i - c.x;
        int y = j - c.y;
        if (x * x + y * y < c.r * c.r) {
          mySetRGB(posmod(i, width), posmod(j, height), rgb);
        }
      }
    }
  }

  public static void mySetRGB(int i, int j, int rgb) {
    img.setRGB(i, j, 1, 1, new int[]{rgb}, 0, 1);
  }

  public static int posmod(int a, int b) {
    return ((a % b) + b) % b; // non-negative modulo
  }

  public static int randomColor() {
    float red = (float) Math.random();
    float green = (float) Math.random();
    float blue = (float) Math.random();
    return new Color(red, green, blue).getRGB();
  }

  public static Circle randomCircle() {
    int x = (int) (Math.random() * width);
    int y = (int) (Math.random() * height);
    int radius = (int) (Math.random() * r);
    return new Circle(x, y, radius);
  }


  /**
   * synchronization methods
   */

  // set circle1 or circle2 to a new random circle if thread1 or thread2, but
  // only return after the other thread is finished drawing if circles overlap
  synchronized public static void setRandomCircleSynch(int threadID) throws Exception {
    if (threadID == 1) {
      Circle c = randomCircle();
      while (circle2 != null && circlesOverlap(c, circle2)) {
        q1.class.wait();
      }
      circle1 = c;
    } else if (threadID == 2) {
      Circle c = randomCircle();
      while (circle1 != null && circlesOverlap(circle1, c)) {
        q1.class.wait();
      }
      circle2 = c;
    }
  }

  // draw circle1 or circle2 if thread2 or thread2, and notify a waiting thread when done drawing
  public static void drawCircleSynch(int threadID, int rgb) throws Exception {
    String indent = threadID == 1 ? "" : "\t\t\t\t";
    if (threadID == 1) {
      drawCircle(circle1, rgb);
      circle1 = null;
    } else if (threadID == 2) {
      drawCircle(circle2, rgb);
      circle2 = null;
    }
    synchronized (q1.class) {
      q1.class.notify();
    }
  }


  /**
   * main method
   */
  public static void main(String[] args) {
    try {
      if (args.length < 3)
        throw new Exception("Missing arguments, only " + args.length + " were specified!");
      // arg 0 is the max radius
      r = Integer.parseInt(args[0]);
      // arg 1 is count
      c = Integer.parseInt(args[1]);
      // arg 2 is a boolean
      multithreaded = Boolean.parseBoolean(args[2]);

      // create an image and initialize it to all 0's
      img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      for (int i = 0; i < width; i++) {
        for (int j = 0; j < height; j++) {
          img.setRGB(i, j, 0);
        }
      }

      long startTime = System.currentTimeMillis();

      if (!multithreaded) {
        for (int i = 0; i < c; i++) {
          Circle circle = randomCircle();
          drawCircle(circle, randomColor());
        }

        // here's where the fun starts
      } else {
        class CircleDrawer extends Thread {
          int id;
          int numCircles;

          CircleDrawer(int pid, int pnumCircles) {
            id = pid;
            numCircles = pnumCircles;
          }

          @Override
          public void run() {
            for (int i = 0; i < numCircles; i++) {
              try {
                setRandomCircleSynch(id);
                drawCircleSynch(id, randomColor());
              } catch (Exception e) {
                System.out.println("ERROR " + e);
                e.printStackTrace();
              }
            }
          }
        }

        CircleDrawer[] circleDrawers = new CircleDrawer[2];
        for (int i = 0; i < circleDrawers.length; i++)
          circleDrawers[i] = new CircleDrawer(i + 1, c / 2);
        if (c % 2 == 1)
          circleDrawers[0].numCircles += 1;

        for (int i = 0; i < circleDrawers.length; i++)
          circleDrawers[i].start();
        for (int i = 0; i < circleDrawers.length; i++)
          circleDrawers[i].join();
      }

      long endTime = System.currentTimeMillis();
      System.out.println(endTime - startTime);

      // Write out the image
      File outputfile = new File("test.png");
      ImageIO.write(img, "png", outputfile);

    } catch (Exception e) {
      System.out.println("ERROR " + e);
      e.printStackTrace();
    }
  }
}


/**
 * a simple class to hold the data of a circle: a coordinate for the center and a radius
 */
class Circle {
  int x;
  int y;
  int r;

  Circle(int x, int y, int r) {
    this.x = x;
    this.y = y;
    this.r = r;
  }
}