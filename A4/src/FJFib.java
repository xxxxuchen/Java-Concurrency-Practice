import java.util.*;
import java.util.concurrent.*;

class FJFib extends RecursiveTask<Integer> {
    private int v;

    FJFib(int v) {
        super();
        this.v = v;
    }

    @Override
    protected Integer compute() {
        int count;
        if (v==0) return 0;
        if (v==1) return 1;

        RecursiveTask<Integer> f1 = new FJFib(v-2);
        RecursiveTask<Integer> f2 = new FJFib(v-1);
        f1.fork();
        f2.fork();
        count = f1.join();
        count += f2.join();
        return count;
    }

    public static void main(String[] args) {
        ForkJoinPool forkJoinPool = new ForkJoinPool();
        int i = Integer.parseInt(args[0]);
        int f = forkJoinPool.invoke(new FJFib(i));
        System.out.println("Fib of "+i+" is "+f);

    }

}
