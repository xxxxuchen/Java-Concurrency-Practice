
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class q1p {
  public static int m = 30;
  public static int n = 30;
  public static int s = 20;
  public static int t = 50;

  public static void main(String[] args) {
    if (args.length != 4) {
      System.out.println("using default m =" + m + ", n =" + n + ", s =" + s + ", t =" + t);
    } else {
      m = Integer.parseInt(args[0]);
      n = Integer.parseInt(args[1]);
      s = Integer.parseInt(args[2]);
      t = Integer.parseInt(args[3]);
    }

    ExecutorService gameExecutor = Executors.newFixedThreadPool(t);


    List<Character> characters = new ArrayList<>();

    Game game = new Game(m);

    // place the n characters at random, non-overlapping points on the perimeter of the grid.
    for (int i = 0; i < n; i++) {
      characters.add(game.placeAtPerimeter());
    }

    for (Character character : characters) {
      gameExecutor.submit(() -> {
        try {
          character.move(gameExecutor);
        } catch (InterruptedException | ExecutionException e) {
          // Thread interrupted, terminate gracefully
        }
      });
    }

    // submit a new task that generates a random character on perimeter every s milliseconds
    gameExecutor.submit(() -> {
      try {
        while (!Thread.currentThread().isInterrupted()) {
          Thread.sleep(s);
          Character c = game.placeAtPerimeter();
          gameExecutor.submit(() -> {
            try {
              c.move(gameExecutor);
            } catch (InterruptedException | ExecutionException e) {
              // Thread interrupted, terminate gracefully
              System.out.println("character thread terminated!");
            }
          });
        }
      } catch (InterruptedException e) {
        // Thread interrupted, terminate gracefully
        System.out.println("spawn thread terminated!");
      }
    });

    // run for 10 seconds and then terminate all threads
    try {
      Thread.sleep(10000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    gameExecutor.shutdownNow();

    // wait for all threads to terminate

    try {
      gameExecutor.awaitTermination(60, TimeUnit.SECONDS);
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

    public Character placeAtPerimeter() {
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

    private Character tryPlaceCharacter(int x, int y) {
      Character character = null;
      cellLocks[x][y].lock();
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

    // check if the target cell is empty, if not, move the character to the target cell
    // if there is a collision, remove both characters from the grid and return false
    public boolean moveCharacter(Cell source, Cell target) {
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
          return false;
        }
        // Check if the target cell is empty
        if (grid[target.x][target.y] == null) {
          // Move the character to the target cell
          grid[target.x][target.y] = grid[source.x][source.y];
          grid[source.x][source.y] = null;
          successCounter.incrementAndGet();
          return true;
        } else {
          // If there is a collision, remove both characters from the grid and return false
          System.out.println("collision detected!");
          grid[source.x][source.y] = null;
          grid[target.x][target.y] = null;
          return false;
        }
      } finally {
        // Release the locks
        cellLocks[firstCell.x][firstCell.y].unlock();
        cellLocks[secondCell.x][secondCell.y].unlock();
      }
    }


  }

  static class Character {
    Cell position;
    private Game game;

    public Character(Game game, Cell position) {
      this.game = game;
      this.position = position;
    }

    public void move(ExecutorService gameExecutor) throws InterruptedException, ExecutionException {
      while (!Thread.currentThread().isInterrupted()) {
        Cell cell = generateRandomDest();
        Cell copyCell = new Cell(position.x, position.y);
        Cell[] plan = computeMovingPlan(copyCell, cell);
        for (int i = 1; i < plan.length; i++) {
          Cell target = plan[i];
          Future<Boolean> isSuccess = gameExecutor.submit(() -> {
            if (game.moveCharacter(position, target)) {
              position = target;
              return true;
            }
            return false;
          });
          if (isSuccess.get()) {
            Thread.sleep(20);
          } else {
            return;
          }
        }
      }
    }

    /*
     For this use the Bresenham algorithm to compute the series of steps forming a “straight” line
     to their goal, given the 8-way movement model.
     */
    Cell[] computeMovingPlan(Cell source, Cell target) {
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

    Cell generateRandomDest() {
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
      return "Character at " + position;
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
