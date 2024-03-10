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
      } catch (CollisionAtPerimeterException e) {
        // collision detected, retry
        i--;
      }
    }

    // start the movement of the characters
    for (Character character : characters) {
      character.start();
    }


    // create another thread that generates a random character on perimeter every s milliseconds
    Thread spawnThread = new Thread(() -> {
      try {
        while (!Thread.currentThread().isInterrupted()) {
          Thread.sleep(s);
          Character c = null;
          try {
            c = game.placeAtPerimeter();
          } catch (CollisionAtPerimeterException e) {
            // collision detected, retry
            continue;
          }
          synchronized (characters) {
            characters.add(c);
          }
          c.start();
        }
      } catch (InterruptedException e) {
        // Thread interrupted, terminate gracefully
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

    System.out.println("total sum of successful moves: " + Game.successCounter.get());

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

    public Character placeAtPerimeter() throws CollisionAtPerimeterException {
      Random rand = new Random();
      int x, y;

      Character character = null;

      int side = rand.nextInt(4); // Randomize spawn point side

      switch (side) {
        case 0: // Top side
          x = 0;
          y = rand.nextInt(gridSize);
          character = tryPlaceCharacter(x, y);
          break;
        case 1: // Right side
          y = gridSize - 1;
          x = rand.nextInt(gridSize);
          character = tryPlaceCharacter(x, y);
          break;
        case 2: // Bottom side
          x = gridSize - 1;
          y = rand.nextInt(gridSize);
          character = tryPlaceCharacter(x, y);
          break;
        case 3: // Left side
          y = 0;
          x = rand.nextInt(gridSize);
          character = tryPlaceCharacter(x, y);
          break;
      }
      return character;
    }

    private Character tryPlaceCharacter(int x, int y) throws CollisionAtPerimeterException {
      Character character = null;
      cellLocks[x][y].lock();
      try {
        if (grid[x][y] == null) {
          character = new Character(this, new Cell(x, y));
          grid[x][y] = character;
        } else {
          grid[x][y].interrupt();
          grid[x][y] = null;
          // throw collision exception
          throw new CollisionAtPerimeterException("collision detected!");
        }
      } finally {
        cellLocks[x][y].unlock();
      }
      return character;
    }

    public void moveCharacter(Cell source, Cell target) throws InterruptedException {
      // Sort the cells to acquire locks in a consistent order to prevent deadlock
      Cell firstCell;
      Cell secondCell;
      if (source.hashCode() < target.hashCode()) {
        firstCell = source;
        secondCell = target;
      } else {
        firstCell = target;
        secondCell = source;
      }
      // Acquire locks for both the source and target cells
      cellLocks[firstCell.x][firstCell.y].lock();
      cellLocks[secondCell.x][secondCell.y].lock();

      try {
        if (grid[source.x][source.y] == null) {
          throw new InterruptedException();
        }
        // Check if the target cell is empty
        if (grid[target.x][target.y] == null) {
          // Move the character to the target cell
          grid[target.x][target.y] = grid[source.x][source.y];
          grid[source.x][source.y] = null;
          successCounter.incrementAndGet();
        } else {
          // If there is a collision, remove both characters from the grid and return false
//          System.out.println("collision detected!");
          grid[source.x][source.y].interrupt();
          grid[target.x][target.y].interrupt();
          grid[source.x][source.y] = null;
          grid[target.x][target.y] = null;
        }
      } finally {
        // Release the locks
        cellLocks[firstCell.x][firstCell.y].unlock();
        cellLocks[secondCell.x][secondCell.y].unlock();
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
    private int x;
    private int y;

    public Cell(int x, int y) {
      this.x = x;
      this.y = y;
    }

    @Override
    public String toString() {
      return "(" + x + "," + y + ")";
    }
  }

  // customs CollisionAtPerimeterException
  private static class CollisionAtPerimeterException extends Exception {
    public CollisionAtPerimeterException(String message) {
      super(message);
    }
  }
}

