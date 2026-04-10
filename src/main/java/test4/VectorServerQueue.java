package test4;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class VectorServerQueue {

    private static final BlockingQueue<double[]> queue = new LinkedBlockingQueue<>();

    public static void push(double[] v) {
        queue.offer(v);
    }

    public static double[] take() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            return new double[]{-1, -1, -1, -1};
        }
    }
}
