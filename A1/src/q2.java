import java.awt.image.*;
import java.io.*;
import javax.imageio.*;

public class q2 {

  public static BufferedImage imgout;

  // parameters and their default values
  public static String imagebase = "cat"; // base name of the input images; actual names append "1.png", "2.png" etc.
  public static int threads = 6; // number of threads to use
  public static int outputheight = 2048; // output image height
  public static int outputwidth = 4096; // output image width
  public static int attempts = 500; // number of failed attempts before a thread gives up

  private static int IMG_DIVISIONS; // divide the output image into IMG_DIVISIONS parts
  private static Object[] locks; // each part need a lock to access
  public static long duration = 0;

  // print out command-line parameter help and exit
  public static void help(String s) {
    System.out.println("Could not parse argument \"" + s + "\".  Please use only the following arguments:");
    System.out.println(" -i inputimagebasename (string; current=\"" + imagebase + "\")");
    System.out.println(" -h outputimageheight (integer; current=\"" + outputheight + "\")");
    System.out.println(" -w outputimagewidth (integer; current=\"" + outputwidth + "\")");
    System.out.println(" -a attempts (integer value >=1; current=\"" + attempts + "\")");
    System.out.println(" -t threads (integer value >=1; current=\"" + threads + "\")");
    System.exit(1);
  }

  // process command-line options
  public static void opts(String[] args) {
    int i = 0;

    try {
      for (; i < args.length; i++) {

        if (i == args.length - 1) help(args[i]);

        if (args[i].equals("-i")) {
          imagebase = args[i + 1];
        } else if (args[i].equals("-h")) {
          outputheight = Integer.parseInt(args[i + 1]);
        } else if (args[i].equals("-w")) {
          outputwidth = Integer.parseInt(args[i + 1]);
        } else if (args[i].equals("-t")) {
          threads = Integer.parseInt(args[i + 1]);
        } else if (args[i].equals("-a")) {
          attempts = Integer.parseInt(args[i + 1]);
        } else {
          help(args[i]);
        }
        // an extra increment since our options consist of 2 pieces
        i++;
      }
    } catch (Exception e) {
      System.err.println(e);
      help(args[i]);
    }
  }

  // main.  we allow an IOException in case the image loading/storing fails.
  public static void main(String[] args) throws IOException {
    // process options
    opts(args);

    long startTime;
    long endTime;

    // Initialize the output image
    imgout = new BufferedImage(outputwidth, outputheight, BufferedImage.TYPE_INT_ARGB);

    // create threads and icons
    Thread[] iconThreads = new Thread[threads];
    BufferedImage[] icons = new BufferedImage[threads];

    int maxIconWidth = 0;
    for (int i = 0; i < threads; i++) {
      icons[i] = ImageIO.read(new File(imagebase + (i + 1) + ".png"));
      maxIconWidth = Math.max(maxIconWidth, icons[i].getWidth());
    }

    // find the appropriate number of divisions (locks)
    IMG_DIVISIONS = (outputwidth / (maxIconWidth * 2)) - 1;
    if (IMG_DIVISIONS <= 2) {
      System.err.println("Please use a larger output image to leverage multi-threading.");
      System.exit(1);
    }
    locks = new Object[IMG_DIVISIONS];

    // Initialize locks
    for (int i = 0; i < IMG_DIVISIONS; i++) {
      locks[i] = new Object();
    }

    startTime = System.currentTimeMillis();
    for (int threadId = 0; threadId < threads; threadId++) {
      iconThreads[threadId] = new Thread(new IconDrawer(threadId, icons[threadId]));
      iconThreads[threadId].start();
    }

    // wait for all threads to finish
    for (Thread thread : iconThreads) {
      try {
        thread.join();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    endTime = System.currentTimeMillis();
    duration = endTime - startTime;
    System.out.println("Duration: " + duration);

    // write out the image
    File outputfile = new File("outputimage.png");
    ImageIO.write(imgout, "png", outputfile);
  }

  private static class IconDrawer implements Runnable {
    private final int aThreadID;
    private final BufferedImage aThreadIcon;

    public IconDrawer(int pThreadId, BufferedImage pThreadIcon) {
      aThreadID = pThreadId;
      aThreadIcon = pThreadIcon;
    }

    @Override
    public void run() {
      try {
        copyIcon();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    private void copyIcon() throws IOException {

      int inWidth = aThreadIcon.getWidth();
      int inHeight = aThreadIcon.getHeight();

      int attemptsLeft = attempts;

      while (attemptsLeft > 0) {

        int x = (int) (Math.random() * (outputwidth - inWidth));
        int y = (int) (Math.random() * (outputheight - inHeight));

        int segmentLength = outputwidth / IMG_DIVISIONS;
        // the part of image that would be locked
        int lockIndex = x / (segmentLength);

        // if the random icon is located right near the division, then lock the left part to avoid overlap
        if ((x % (segmentLength)) <= inWidth) {
          if (lockIndex > 0) lockIndex--;
        }

        // lock it's part, other threads can still draw on the other part of the image concurrently
        synchronized (locks[lockIndex]) {
          if (isOverlap(x, y, inWidth, inHeight)) {
            attemptsLeft--;
            continue;
          }

          for (int i = 0; i < inWidth; i++) {
            for (int j = 0; j < inHeight; j++) {
              imgout.setRGB(x + i, y + j, aThreadIcon.getRGB(i, j));
            }
          }
        }
      }

    }

    private boolean isOverlap(int x, int y, int width, int height) {
      for (int i = 0; i < width; i++) {
        for (int j = 0; j < height; j++) {
          int pixel = imgout.getRGB(x + i, y + j);
          if ((pixel >> 24) != 0x00) {
            return true;
          }
        }
      }
      return false;
    }
  }
}
