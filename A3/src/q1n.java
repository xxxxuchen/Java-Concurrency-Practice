import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class q1n {
  public static int m = 30;
  public static int n = 30;
  public static int s = 20;


  public static void main(String[] args) {
    if (args.length != 3) {
      System.out.println("using default m =" + m + ", n =" + n + ", s =" + s);
    } else {
      m = Integer.parseInt(args[0]);
      n = Integer.parseInt(args[1]);
      s = Integer.parseInt(args[2]);
    }

    List<Character> characters = new ArrayList<>();

    Game game = new Game(m);

    // place the n characters at random, non-overlapping points on the perimeter of the grid.
    for (int i = 0; i < n; i++) {
      try {
        characters.add(game.placeAtPerimeter());
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }


    // create another thread that generates a random character on perimeter every s milliseconds
    Thread spawnThread = new Thread(() -> {
      try {
        while (true) {
          Thread.sleep(s);
          Character c = game.placeAtPerimeter();
//          System.out.println(game);
          synchronized (characters) {
            characters.add(c);
          }
          c.start();
        }
      } catch (InterruptedException e) {
        // Thread interrupted, terminate gracefully
        System.out.println("spawn thread terminated!");
      }
    });
    spawnThread.start();


    // run for 10 seconds and then terminate all threads
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    try {
      spawnThread.interrupt();
      spawnThread.join();
      for (Character c : characters) {
        c.interrupt();
        c.join();
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    System.out.println(Game.successCounter);

  }

  static class Game {
    private Character[][] grid;
    private int gridSize;
    private final Lock[][] cellLocks;

    public static AtomicInteger successCounter = new AtomicInteger(0);

    public Game(int pSize) {
      gridSize = pSize;
      grid = new Character[pSize][pSize];
      cellLocks = new ReentrantLock[gridSize][gridSize];
      for (int i = 0; i < gridSize; i++) {
        for (int j = 0; j < gridSize; j++) {
          cellLocks[i][j] = new ReentrantLock();
        }
      }
    }

    public Character placeAtPerimeter() throws InterruptedException {
      Random rand = new Random();
      int x, y;

      Character character = null;

      int side = rand.nextInt(4); // Randomize spawn point side

      switch (side) {
        case 0: // Top side
          x = 0;
          do {
            y = rand.nextInt(gridSize);
            character = tryPlaceCharacter(x, y);
          } while (character == null);
          break;
        case 1: // Right side
          y = gridSize - 1;
          do {
            x = rand.nextInt(gridSize);
            character = tryPlaceCharacter(x, y);
          } while (character == null);
          break;
        case 2: // Bottom side
          x = gridSize - 1;
          do {
            y = rand.nextInt(gridSize);
            character = tryPlaceCharacter(x, y);
          } while (character == null);
          break;
        case 3: // Left side
          y = 0;
          do {
            x = rand.nextInt(gridSize);
            character = tryPlaceCharacter(x, y);
          } while (character == null);
          break;
        default:
          x = 0;
          y = 0;
          break;
      }
      return character;
    }

    private Character tryPlaceCharacter(int x, int y) throws InterruptedException {
      Character character = null;
      cellLocks[x][y].lockInterruptibly();
      try {
        if (grid[x][y] == null) {
          character = new Character(this, new Cell(x, y));
          grid[x][y] = character;
        }
      } finally {
        cellLocks[x][y].unlock();
      }
      return character;
    }

    public void moveCharacter(Cell source, Cell target) throws InterruptedException {
      // acquire its source lock first, then use try lock to acquire the target lock
      cellLocks[source.x][source.y].lockInterruptibly();
      if (cellLocks[target.x][target.y].tryLock()) {
        // move the character
        grid[target.x][target.y] = grid[source.x][source.y];
        grid[source.x][source.y] = null;
        successCounter.incrementAndGet();
        cellLocks[target.x][target.y].unlock();
        cellLocks[source.x][source.y].unlock();
      } else {
        // terminate both characters
        if (grid[source.x][source.y] != null) grid[source.x][source.y].interrupt();
        if (grid[target.x][target.y] != null) grid[target.x][target.y].interrupt();
        // remove both character in the grid
        grid[source.x][source.y] = null;
        grid[target.x][target.y] = null;
        System.out.println("collision detected!");
        cellLocks[source.x][source.y].unlock();
      }
    }

    @Override
    public synchronized String toString() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < gridSize; i++) {
        for (int j = 0; j < gridSize; j++) {
          if (grid[i][j] != null) {
            sb.append(grid[i][j].toString());
          } else {
            sb.append("00");
          }
          sb.append(" ");
        }
        sb.append("\n");
      }
      return sb.toString();
    }

  }

  static class Character extends Thread {
    private Cell position;
    private Game game;

    public Character(Game game, Cell position) {
      this.game = game;
      this.position = position;
    }

    @Override
    public void run() {
      try {
        while (!isInterrupted()) {
          Cell dest = generateRandomDest();
          Cell copyCell = new Cell(position.x, position.y);
          Cell[] plan = computeMovingPlan(copyCell, dest);
          for (int i = 1; i < plan.length; i++) {
            game.moveCharacter(position, plan[i]);
            position = plan[i];
            Thread.sleep(20);
          }
        }
      } catch (InterruptedException e) {
        // Thread interrupted, terminate gracefully
      }
    }


    /*
     For this use the Bresenham algorithm to compute the series of steps forming a “straight” line
     to their goal, given the 8-way movement model.
     */
    private Cell[] computeMovingPlan(Cell source, Cell target) {
      List<Cell> plan = new ArrayList<>();
      int dx = Math.abs(target.x - source.x);
      int dy = Math.abs(target.y - source.y);
      int sx = source.x < target.x ? 1 : -1;
      int sy = source.y < target.y ? 1 : -1;
      int err = dx - dy;
      int e2;
      while (true) {
        plan.add(new Cell(source.x, source.y));
        if (source.x == target.x && source.y == target.y) {
          break;
        }
        e2 = 2 * err;
        if (e2 > -dy) {
          err -= dy;
          source.x += sx;
        }
        if (e2 < dx) {
          err += dx;
          source.y += sy;
        }
      }
      return plan.toArray(new Cell[0]);

    }

    private Cell generateRandomDest() {
      Random rand = new Random();
      int x = position.x + rand.nextInt(21) - 10;
      int y = position.y + rand.nextInt(21) - 10;
      // avoid the same position
      if (x == position.x && y == position.y) {
        x = position.x + 5;
        y = position.y + 5;
      }
      x = Math.max(0, Math.min(game.gridSize - 1, x));
      y = Math.max(0, Math.min(game.gridSize - 1, y));
      return new Cell(x, y);
    }

    @Override
    public String toString() {
      // print the thread id
      return Long.toString(this.getId());
    }
  }

  static class Cell {
    public int x;
    public int y;

    public Cell(int x, int y) {
      this.x = x;
      this.y = y;
    }

    @Override
    public String toString() {
      return "(" + x + "," + y + ")";
    }
  }

}

