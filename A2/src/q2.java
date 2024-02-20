import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

public class q2 {
  public static int n = 5; // number of road segments
  public static int s = 150; // time interval of vehicle generation
  public static int d = 50; // time taken to move one road segment

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
    private int nextVId = 1; // Next vehicle that should enter the road
    private final Queue<Vehicle> vehicles = new LinkedList<>();
    private int numVichicle = 0; // Num of vehicles on the road

    public Road(int segmentCount, int segmentTime) {
      this.segmentCount = segmentCount;
      this.segmentTime = segmentTime;
    }

    public void addVehicle(Vehicle vehicle) {
      synchronized (lock) {
        vehicles.add(vehicle);
      }
    }

    public void enter(Vehicle vehicle) throws InterruptedException {
      synchronized (lock) {
        Vehicle v = vehicles.peek();
        int segment = v.segment;
        int direction = v.direction;

        // First vehicle enters the road when the road is empty
        if (currentDirection == 0) {
          currentDirection = direction;
        }
        // Wait if traffic is moving in opposite direction or the vehicle is not the next to enter
        while (currentDirection != direction || vehicle.id != nextVId) {
          lock.wait();
        }
        nextVId++;
        numVichicle++;
        System.out.println("enter: " + vehicle.id + "," + segment);
      }
    }

    public void move() throws InterruptedException {
      Vehicle v;
      synchronized (lock) {
        v = vehicles.poll();
      }
      // Move the vehicles through the road segments concurrently
      int segment = v.segment;
      int direction = v.direction;
      for (int i = 0; i < segmentCount - 1; i++) {
        Thread.sleep(segmentTime);
        segment = (segment + direction + segmentCount) % segmentCount; // Move to next segment
        System.out.println("enter: " + v.id + "," + segment);

        // If the vehicle enters from residential area, it will leave the road beforehand
        if (segment == 0 || segment == segmentCount - 1) {
          break;
        }

      }
    }

    public void exit(Vehicle vehicle) {
      synchronized (lock) {
        numVichicle--;
        System.out.println("exit: " + vehicle.id);
        if (numVichicle == 0) {
          if (vehicles.peek() == null) {
            currentDirection = 0;
          } else {
            // Let the next in order vehicle to enter the road and set the direction
            currentDirection = vehicles.peek().direction;
          }
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
            // enter at residential area
            entryPoint = rand.nextInt(road.segmentCount - 2) + 1;
            direction = rand.nextBoolean() ? 1 : -1;
          }
          Vehicle vehicle = new Vehicle(id++, entryPoint, direction);
          road.addVehicle(vehicle);
          System.out.println("car: " + vehicle.id + "," + "entry point:" +
            entryPoint + "," + "direction:" + direction);
          // start a new thread that is responsible for moving a vehicle
          new Thread(() -> {
            try {
              road.enter(vehicle);
              road.move();
              road.exit(vehicle);
            } catch (InterruptedException e) {
              e.printStackTrace();
            }
          }).start();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }
  }

  static class Vehicle {
    private final int id;
    private int segment;
    private final int direction;

    public Vehicle(int id, int segment, int direction) {
      this.id = id;
      this.segment = segment;
      this.direction = direction;
    }
  }
}
