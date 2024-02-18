/*
 * Copyright (c) 2019-2023. Michael Pogrebinsky - Top Developer Academy
 * https://topdeveloperacademy.com
 * All rights reserved
 */

/**
 * Race Conditions & Data Races
 * https://www.udemy.com/java-multithreading-concurrency-performance-optimization
 */
public class Main {
  public static void main(String[] args) {
    SharedClass sharedClass = new SharedClass();
    Thread thread1 = new Thread(() -> {
      for (int i = 0; i < Integer.MAX_VALUE; i++) {
        sharedClass.increment();
      }
    });

    Thread thread2 = new Thread(() -> {
      for (int i = 0; i < Integer.MAX_VALUE; i++) {
        sharedClass.checkForDataRace();
      }

    });

    thread1.start();
    thread2.start();
  }

  public static class SharedClass {
    private int x = 0;
    volatile private int y = 0;

    public void increment() {

      y++;
      x++;
    }

    public void checkForDataRace() {
      if (y > x) {
        System.out.println("y > x - Data Race is detected");
      }
    }
  }
}
