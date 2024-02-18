import java.util.ArrayList;
import java.util.List;

public class q1 {
  public static void main(String[] args) {
    if (args.length != 2) System.out.println("wrong number of arguments");
    System.out.println(args[0] + " " + args[1]);

    // create four stations
    List<Thread> stationThreads = List.of(
      new StationA(),
      new StationB(),
      new StationC(),
      new StationD());

    Overseer overseer = new Overseer(Integer.parseInt(args[0]), Integer.parseInt(args[1]));

    // register overseer's observers (stations)
    for (Thread stationThread : stationThreads) {
      overseer.addStation((Station) stationThread);
    }

    overseer.start();

    for (Thread stationThread : stationThreads) {
      stationThread.start();
    }
  }

  private static class Overseer extends Thread {
    private int aTotal = 0;
    private final int aMax;
    private final int aCheckFrequency;
    private final List<Station> aStations = new ArrayList<>();

    /**
     * @param pMax            the maximum capacity allowed in the metro system
     * @param pCheckFrequency the milliseconds at which the overseer checks the capacity of the system
     * @pre pMax >= 10 && pCheckFrequency >= 10
     */
    public Overseer(int pMax, int pCheckFrequency) {
      assert pMax >= 10 && pCheckFrequency >= 10;
      aMax = pMax;
      aCheckFrequency = pCheckFrequency;
    }

    public void addStation(Station pStation) {
      aStations.add(pStation);
    }

    // callback, overseer does not set Full itself
    private void notifyStationsFull(boolean pIsFull) {
      for (Station station : aStations) {
        station.handleFull(pIsFull);
      }
    }

    private void notifyStationsStop(boolean pStop) {
      for (Station station : aStations) {
        station.handleStop(pStop);
      }
    }

    @Override
    public void run() {
      int tempFrequency = aCheckFrequency;

      while (true) {
        try {
          Thread.sleep(tempFrequency);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        // metro has not been full yet, stop all the stations at aCheckFrequency
        if (tempFrequency == aCheckFrequency) {
          notifyStationsStop(true);
        }

        int sum = 0;
        for (Station station : aStations) {
          // protected by synchronized block to avoid race condition
          int localCount = station.getLocalCount();
          sum += localCount;
          System.out.println(station.getName() + " count: " + localCount);
        }
        System.out.println("TotalCount: " + sum);

        aTotal = sum;
        if (aTotal >= aMax) {
          System.out.println("Metro is full");
          // stations should not be stopped in order to let people out
          notifyStationsStop(false);
          notifyStationsFull(true);
          tempFrequency = aCheckFrequency / 10;
        } else if (tempFrequency != aCheckFrequency && aTotal < Math.floor(0.75 * aMax)) {
          notifyStationsFull(false);
          // set checking frequency back to the normal frequency
          tempFrequency = aCheckFrequency;
        } else { // only when metro is not full and overseer checks at normal frequency
          System.out.println("Metro is not full");
          notifyStationsStop(false);
        }
      }
    }
  }


  private static class Station extends Thread implements Observer {
    private int aCount = 0;
    private volatile boolean aIsFull; // determine whether metro is full
    private volatile boolean aStop; // determine whether it should stop letting people in and out

    protected Station() {
      this.setName(this.getClass().getSimpleName());
    }

    @Override
    public void start() {
      System.out.println("Starting thread: " + this.getName());
      super.start();
    }


    @Override
    public void handleFull(boolean pIsFull) {
      aIsFull = pIsFull;
    }

    @Override
    public void handleStop(boolean pStop) {
      aStop = pStop;
    }

    public synchronized int getLocalCount() {
      return aCount;
    }

    private synchronized void incrementLocalCount() {
      aCount++;
    }

    private synchronized void decrementLocalCount() {
      aCount--;
    }

    @Override
    public void run() {
      while (true) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
//        System.out.println(this.getName() + " " + "stop is: " + aStop + " full is: " + aIsFull);
        if (!aStop) {
          if (aIsFull) {
            decrementLocalCount();
          } else {
            if (Math.random() <= 0.51) {
              incrementLocalCount();
            } else {
              decrementLocalCount();
            }
          }
        }
      }
    }
  }

  private static class StationA extends Station {
  }

  private static class StationB extends Station {
  }

  private static class StationC extends Station {
  }

  private static class StationD extends Station {
  }

  private interface Observer {
    void handleFull(boolean pIsFull);

    void handleStop(boolean pStop);
  }
}
