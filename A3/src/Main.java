import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicStampedReference;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.Exchanger;

public class Main {

  public static int x = 1; // 0 for lock-free stack, 1 for elimination stack
  public static int t = 10; // number of threads
  public static int n = 20; // number of operations per thread
  public static int s = 10; // 0-s range of sleep time after each operation
  public static int e = 20; // capacity of the elimination array
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


    /**
     * stack is tested by starting t threads that attempt PUSH or POP
     * operations on the stack, each operation being either a push or pop, selected with even odds.
     * After each push/pop operation, a thread sleeps for a random time, 0–s ms.
     * Each thread will attempt to perform n operations
     */

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
    System.out.println("Time taken: " + (endTime - startTime) + "ms" + " Number of nodes remaining: " + stackSize);

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
    public void run()  {
      Random random = new Random();
      for (int i = 0; i < n; i++) {
        if (random.nextBoolean()) {
          stack.push(random.nextInt());
        } else {
          try {
            stack.pop();
          } catch (EmptyStackException e) {
            System.out.println("stack is empty");
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

    protected boolean tryPush(Node v) {
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
      System.out.println("pushed " + value);
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
        return currentHeadNode;
      } else {
        return null;
      }
    }

    public T pop() throws EmptyStackException{
      Node<T> returnNode;
      while (true) {
        returnNode = tryPop();
        if (returnNode != null) {
          System.out.println("popped " + returnNode.value);
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

    private static ThreadLocal<BoundedStack<Node>> localStack =
      ThreadLocal.withInitial(() -> new BoundedStack<>(LAST_N_NODES));

    public EliminationStack(int capacity, int timeOut) {
      exchangers = (Exchanger<T>[]) new Exchanger[capacity];
      for (int i = 0; i < capacity; i++) {
        exchangers[i] = new Exchanger<>();
      }
      this.timeOut = timeOut;
    }

    /**
     * A thread may PUSH either a newly allocated node, or use an old, previously popped node.
     * To support the latter it should retain the last 50 nodes it has popped, and when performing a PUSH,
     * if it has any old nodes stored, then 50% of the time it should reuse an old node.
     */
    public void push(T value) {
      Node newNode = new Node(value);
      Node oldNode = localStack.get().pop();
      if (oldNode != null && Math.random() < 0.5) {
        newNode = oldNode;
      }
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
            // retry
          }
        }
      }
    }

    public T pop() throws EmptyStackException {
      Node<T> returnNode;
      while (true) {
        returnNode = tryPop();
        if (returnNode != null) {
          returnNode.next = null;
          counter.decrementAndGet();
          localStack.get().push(returnNode);
          return returnNode.value;
        } else {
          int randomIndex = (int) (Math.random() * exchangers.length);
          try {
            Node otherNode = (Node) exchangers[randomIndex].exchange(null, timeOut, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (otherNode != null) {
              localStack.get().push(otherNode);
              return (T) otherNode.value;
            }
          } catch (InterruptedException e) {
            e.printStackTrace();
          } catch (TimeoutException e) {
            // retry
          }
        }
      }
    }

  }

  private static class BoundedStack<T> {
    private int maxSize;
    private Deque<T> stack;

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
