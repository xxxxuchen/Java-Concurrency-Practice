import java.util.Random;public class q1 {  public static int k = 4; // number of sea creatures  public static int n = 5; // simulation time in seconds  public static void main(String[] args) {    if (args.length != 2) {      System.out.println("using default k =" + k + ", n =" + n);    } else {      k = Integer.parseInt(args[0]);      n = Integer.parseInt(args[1]);    }    Volume volume = new Volume(k);    // Initialize sea creatures    SeaCreature[] creatures = new SeaCreature[k];    Shapes[] shapes = {Shapes.SHAPE1, Shapes.SHAPE2, Shapes.SHAPE3, Shapes.SHAPE4};    for (int i = 0; i < k; i++) {      Shapes shape = shapes[i % 4]; // Round-robin selection of creature shape      creatures[i] = new SeaCreature(shape, volume);    }    // Place creatures in non-overlapping positions    creatures[0].setPosition(new int[]{0, 0, 0}); // Place the first creature at the origin    for (int i = 1; i < k; i++) {      int[] previousPosition = creatures[i - 1].getPosition();      int[] newPosition = new int[3];      // Calculate new position diagonally away from the previous creature      for (int j = 0; j < 3; j++) {        newPosition[j] = previousPosition[j] + 2; // Place the new creature 2 units away diagonally      }      creatures[i].setPosition(newPosition);      volume.getGrid()[newPosition[0]][newPosition[1]][newPosition[2]] = creatures[i];      System.out.println("Creature " + creatures[i].id() + " (type " + creatures[i].shape().name() + ") " +        "placed at " + creatures[i].positionToString(newPosition));    }    // Start simulation    for (int i = 0; i < k; i++) {      creatures[i].start();    }    // Run simulation for n seconds    try {      Thread.sleep(n * 1000);    } catch (InterruptedException e) {      // Thread interrupted, terminate gracefully    }    // Interrupt all threads to terminate simulation    for (int i = 0; i < k; i++) {      creatures[i].interrupt();    }  }}enum Shapes {  SHAPE1(new int[][]{{0, 0, 0}, {0, 0, 1}, {0, 0, 2}}),  SHAPE2(new int[][]{{1, 1, 0}, {1, 0, 1}, {0, 1, 1}, {1, 1, 1}, {2, 1, 1}, {1, 2, 1}, {1, 1, 2}}),  SHAPE3(new int[][]{{0, 0, 0}, {1, 0, 0}, {0, 0, 1}, {0, 0, 2}, {0, 1, 2}}),  SHAPE4(new int[][]{{0, 0, 0}, {2, 0, 0}, {0, 0, 1}, {1, 0, 1}, {2, 0, 1}, {1, 0, 2}});  private final int[][] shape;  Shapes(int[][] shape) {    this.shape = shape;  }  public int[][] getShape() {    return shape;  }}class SeaCreature extends Thread {  private static int idCounter = 1;  private final int id;  private final Shapes shape;  private int[] position;  private final Volume volume;  private final Random random;  SeaCreature(Shapes shape, Volume volume) {    this.id = idCounter++;    this.shape = shape;    this.volume = volume;    this.random = new Random();  }  @Override  public void run() {    try {      while (!Thread.interrupted()) {        Thread.sleep(random.nextInt(41) + 10); // sleep for 10-50 ms        int[] newPosition = getRandomMove();        volume.moveCreature(this, newPosition);        System.out.println("Creature " + id + " (type " + shape.name() + ") moved from " +          positionToString(position) + " to " + positionToString(newPosition));        position = newPosition;      }    } catch (InterruptedException e) {      // Thread interrupted, terminate gracefully    }  }  private int[] getRandomMove() {    int[] newPosition = new int[3];    for (int i = 0; i < 3; i++) {      newPosition[i] = position[i] + random.nextInt(3) - 1; // -1, 0, or 1      newPosition[i] = Math.max(0, Math.min(volume.getSize(i) - 1, newPosition[i])); // Ensure within bounds    }    return newPosition;  }  void setPosition(int[] position) {    this.position = position;  }  public int[] getPosition() {    return position;  }  public Shapes shape() {    return shape;  }  public int id() {    return id;  }  public String positionToString(int[] position) {    return "(" + position[0] + "," + position[1] + "," + position[2] + ")";  }}class Volume {  private final Object[][][] cellLocks;  private final SeaCreature[][][] grid;  private final int sizeX;  private final int sizeY;  private final int sizeZ;  Volume(int k) {    this.sizeX = 5 * k;    this.sizeY = 5 * k;    this.sizeZ = 5 * k;    this.cellLocks = new Object[sizeX][sizeY][sizeZ];    this.grid = new SeaCreature[sizeX][sizeY][sizeZ];    // Initialize locks    for (int x = 0; x < sizeX; x++) {      for (int y = 0; y < sizeY; y++) {        for (int z = 0; z < sizeZ; z++) {          cellLocks[x][y][z] = new Object();        }      }    }  }  public boolean moveCreature(SeaCreature creature, int[] newPosition) {    int[] currentPosition = creature.getPosition();    Object currentLock = cellLocks[currentPosition[0]][currentPosition[1]][currentPosition[2]];    Object newLock = cellLocks[newPosition[0]][newPosition[1]][newPosition[2]];    boolean moved = false;    // Acquire locks in the determined order to avoid deadlocks    Object firstLock = currentLock.hashCode() < newLock.hashCode() ? currentLock : newLock;    Object secondLock = currentLock.hashCode() < newLock.hashCode() ? newLock : currentLock;    synchronized (firstLock) {      synchronized (secondLock) {        // Check if any of the cells the creature intends to occupy in its new position are already occupied        boolean destinationOccupied = false;        int[][] shape = creature.shape().getShape();        for (int[] offset : shape) {          int[] cell = new int[]{            // Adjusted to the reference coordinate            newPosition[0] + offset[0] - shape[0][0],            newPosition[1] + offset[1] - shape[0][1],            newPosition[2] + offset[2] - shape[0][2]          };          // Check boundary and overlap          if (cell[0] >= 0 && cell[0] < sizeX && cell[1] >= 0 &&            cell[1] < sizeY && cell[2] >= 0 && cell[2] < sizeZ) {            if (grid[cell[0]][cell[1]][cell[2]] != null && grid[cell[0]][cell[1]][cell[2]] != creature) {              destinationOccupied = true;              System.out.println("Destination cell " + creature.positionToString(cell) + " is occupied");              break;            }          }        }        if (!destinationOccupied) {          // Destination cells are not occupied, move the creature          grid[currentPosition[0]][currentPosition[1]][currentPosition[2]] = null;          grid[newPosition[0]][newPosition[1]][newPosition[2]] = creature;          moved = true;        }      }    }    return moved;  }  public int getSize(int dimension) {    switch (dimension) {      case 0:        return sizeX;      case 1:        return sizeY;      case 2:        return sizeZ;      default:        throw new IllegalArgumentException("Invalid dimension");    }  }  public SeaCreature[][][] getGrid() {    return grid;  }}