import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.Exchanger;

public class q2 {
  public static int x = 1; // 0 for lock-free stack, 1 for elimination stack
  public static int t = 1500; // number of threads
  public static int n = 1500; // number of operations per thread
  public static int s = 5; // 0-s range of sleep time after each operation
  public static int e = 500; // capacity of the elimination array
  public static int w = 50; // timeout for exchanger

  private static int LAST_N_NODES = 50;

  public static void main(String[] args) throws InterruptedException {
    if (args.length != 6) {
      System.out.println("using default x =" + x + ", t =" + t + ", n =" + n + ", s =" + s + ", e =" + e + ", w =" + w);
    } else {
      x = Integer.parseInt(args[0]);
      t = Integer.parseInt(args[1]);
      n = Integer.parseInt(args[2]);
      s = Integer.parseInt(args[3]);
      e = Integer.parseInt(args[4]);
      w = Integer.parseInt(args[5]);
    }

    LockFreeStack<Integer> stack;
    if (x == 0) {
      stack = new LockFreeStack<>();
    } else {
      stack = new EliminationStack<>(e, w);
    }

    // create t threads which each perform n operations on the stack
    Thread[] threads = new Thread[t];
    for (int i = 0; i < t; i++) {
      threads[i] = new Thread(new StackOperationThread(stack, n, s));
    }

    long startTime = System.currentTimeMillis();
    for (int i = 0; i < t; i++) {
      threads[i].start();
    }
    for (int i = 0; i < t; i++) {
      threads[i].join();
    }
    long endTime = System.currentTimeMillis();
    int stackSize = stack.size();
    long timeTaken = endTime - startTime; // ms
    double timeTakenInSeconds = timeTaken / 1000.0; // s
    System.out.println("Time taken: " + timeTaken + "ms" + "/" + timeTakenInSeconds + "s" + " Number of nodes remaining: " + stackSize);

  }

  public static class StackOperationThread implements Runnable {
    private LockFreeStack<Integer> stack;
    private int n;
    private int s;

    public StackOperationThread(LockFreeStack<Integer> stack, int n, int s) {
      this.stack = stack;
      this.n = n;
      this.s = s;
    }

    @Override
    public void run() {
      Random random = new Random();
      for (int i = 0; i < n; i++) {
        if (random.nextBoolean()) {
          stack.push(random.nextInt());
        } else {
          try {
            stack.pop();
          } catch (EmptyStackException e) {
            // stack is empty, ignore and continue the loop
//            System.out.println("stack is empty");
          }
        }
        try {
          Thread.sleep(random.nextInt(s));
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }

  }

  public static class LockFreeStack<T> {
    private AtomicStampedReference<Node<T>> head = new AtomicStampedReference<>(null, 0);
    protected AtomicInteger counter = new AtomicInteger(0);

    // maintain the last 50 nodes popped from the stack
    private static ThreadLocal<BoundedStack<Node>> localStack =
      ThreadLocal.withInitial(() -> new BoundedStack<>(LAST_N_NODES));

    protected boolean tryPush(Node v) {
      Node oldNode = localStack.get().pop();
      // reuse the old node
      if (oldNode != null && Math.random() < 0.5) {
        v = oldNode;
      }
      int[] stampHolder = new int[1];
      Node<T> currentHeadNode = head.get(stampHolder);
      v.next = currentHeadNode;
      return head.compareAndSet(currentHeadNode, v, stampHolder[0], stampHolder[0] + 1);
    }

    public void push(T value) {
      Node<T> newHeadNode = new Node<>(value);

      while (!tryPush(newHeadNode)) {
//        LockSupport.parkNanos(1);
      }
//      System.out.println("pushed " + value);
      counter.incrementAndGet();
    }


    protected Node<T> tryPop() throws EmptyStackException {
      int[] stampHolder = new int[1];
      Node<T> currentHeadNode = head.get(stampHolder);
      if (currentHeadNode == null) {
        throw new EmptyStackException();
      }
      Node<T> newHeadNode = currentHeadNode.next;
      if (head.compareAndSet(currentHeadNode, newHeadNode, stampHolder[0], stampHolder[0] + 1)) {
        currentHeadNode.next = null;
        localStack.get().push(currentHeadNode);
        return currentHeadNode;
      } else {
        return null;
      }
    }

    public T pop() throws EmptyStackException {
      Node<T> returnNode;
      while (true) {
        returnNode = tryPop();
        if (returnNode != null) {
//          System.out.println("popped " + returnNode.value);
          counter.decrementAndGet();
          return returnNode.value;
        } else {
//          LockSupport.parkNanos(1);
        }
      }

    }

    public int size() {
      return counter.get();
    }
  }


  public static class EliminationStack<T> extends LockFreeStack<T> {
    private Exchanger<T>[] exchangers;

    private int timeOut;


    public EliminationStack(int capacity, int timeOut) {
      exchangers = (Exchanger<T>[]) new Exchanger[capacity];
      for (int i = 0; i < capacity; i++) {
        exchangers[i] = new Exchanger<>();
      }
      this.timeOut = timeOut;
    }

    public void push(T value) {
      Node newNode = new Node(value);
      while (true) {
        if (tryPush(newNode)) {
          counter.incrementAndGet();
          return;
        } else {
          int randomIndex = (int) (Math.random() * exchangers.length);
          try {
            T otherValue = exchangers[randomIndex].exchange((T) newNode, timeOut, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (otherValue == null) {
              return;
            }
          } catch (InterruptedException e) {
            e.printStackTrace();
          } catch (TimeoutException e) {
            // retry the lock free stack push
          }
        }
      }
    }

    public T pop() throws EmptyStackException {
      Node<T> returnNode;
      while (true) {
        returnNode = tryPop();
        if (returnNode != null) {
          counter.decrementAndGet();
          return returnNode.value;
        } else {
          int randomIndex = (int) (Math.random() * exchangers.length);
          try {
            Node otherNode = (Node) exchangers[randomIndex].exchange(null, timeOut, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (otherNode != null) {
              return (T) otherNode.value;
            }
          } catch (InterruptedException e) {
            e.printStackTrace();
          } catch (TimeoutException e) {
            // retry the lock free stack pop
          }
        }
      }
    }

  }

  private static class BoundedStack<T> {
    private int maxSize;
    private Deque<T> stack; // hold the last maxSize popped nodes

    public BoundedStack(int maxSize) {
      this.maxSize = maxSize;
      this.stack = new ArrayDeque<>(maxSize);
    }

    public void push(T element) {
      stack.push(element);
      if (stack.size() > maxSize) {
        stack.removeLast();
      }
    }

    public T pop() {
      if (stack.isEmpty()) {
        return null;
      }
      return stack.pop();
    }

  }

  private static class Node<T> {
    public T value;
    public Node<T> next;

    public Node(T value) {
      this.value = value;
      this.next = null;
    }
  }
}
