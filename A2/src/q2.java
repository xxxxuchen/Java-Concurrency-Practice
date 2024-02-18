import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

public class q2 {
  public static int n = 10; // number of road segments
  public static int s = 25; // time interval of vehicle generation
  public static int d = 100; // time taken to move one road segment

  public static void main(String[] args) {
    if (args.length != 3) {
      System.out.println("using default n =" + n + ", s =" + s + ", d =" + d);
    } else {
      n = Integer.parseInt(args[0]);
      s = Integer.parseInt(args[1]);
      d = Integer.parseInt(args[2]);
    }
    if (n <= 2 || s <= 20 || d <= 10) {
      System.err.println("Invalid parameters. n > 2, s > 20, d > 10 required.");
      return;
    }

    Road road = new Road(n, d);
    Thread vehicleGenerator = new Thread(new VehicleGenerator(road, s));
    vehicleGenerator.start();
  }

  static class Road {
    private final Object lock = new Object(); // Monitor lock
    private int currentDirection = 0; // Current direction of traffic flow
    private final int segmentCount; // Number of road segments
    private final int segmentTime; // Time taken to move one road segment
    private final Queue<Integer> vehicles = new LinkedList<>();

    private int numVichicle = 0;

    public Road(int segmentCount, int segmentTime) {
      this.segmentCount = segmentCount;
      this.segmentTime = segmentTime;
    }

    public void enter(int vehicleID, int segment, int direction) throws InterruptedException {
      synchronized (lock) {
        // Wait if traffic is moving in opposite direction
        while (currentDirection != 0 && currentDirection != direction) {
          lock.wait();
        }
        currentDirection = direction;
        numVichicle++;
        vehicles.add(vehicleID);
        System.out.println("enter: " + vehicleID + "," + segment);
      }
    }

    public void move(int vehicleID, int segment, int direction) throws InterruptedException {
      // Move through the road segments
      for (int i = 0; i < segmentCount - 1; i++) {
        Thread.sleep(segmentTime);
        segment = (segment + direction + segmentCount) % segmentCount; // Move to next segment
        System.out.println("enter: " + vehicleID + "," + segment);

        // if the vehicle enter from residential area, it will leave beforehand
        if (segment == 0 || segment == segmentCount - 1) {
          break;
        }

      }
    }

    public void exit(int vehicleID) {
      synchronized (lock) {
        System.out.println("exit: " + vehicleID);
        numVichicle--;
        if (numVichicle == 0) {
          currentDirection = 0;
          lock.notifyAll();
        }
      }
    }


  }

  static class VehicleGenerator implements Runnable {
    private final Road road;
    private final int interval;
    private int id = 1;

    public VehicleGenerator(Road road, int interval) {
      this.road = road;
      this.interval = interval;
    }

    @Override
    public void run() {
      Random rand = new Random();
      while (true) {
        try {
          Thread.sleep(interval + rand.nextInt(41) - 20); // Varying time interval
          int entryPoint;
          int direction;

          // Determine entry point and direction
          int choice = rand.nextInt(100);
          if (choice < 45) {
            entryPoint = 0;
            direction = 1;
          } else if (choice < 90) {
            entryPoint = road.segmentCount - 1;
            direction = -1;
          } else {
            entryPoint = rand.nextInt(road.segmentCount - 2) + 1;
            direction = rand.nextBoolean() ? 1 : -1;
          }

          try {
            System.out.println("car: " + id + "," + "entry point:" +
              entryPoint + "," + "direction:" + direction);
            road.enter(id, entryPoint, direction);
            new Thread(new Vehicle(id++, entryPoint, direction, road)).start();
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  }

  static class Vehicle implements Runnable {
    private final int id;
    private int segment;
    private final int direction;
    private final Road road;

    public Vehicle(int id, int segment, int direction, Road road) {
      this.id = id;
      this.segment = segment;
      this.direction = direction;
      this.road = road;
    }

    @Override
    public void run() {
      try {
        road.move(id, segment, direction);
        road.exit(id);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }
}
