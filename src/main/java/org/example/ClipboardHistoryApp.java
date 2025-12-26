package org.example;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class ClipboardHistoryApp {

    static final class BoundedFifoBuffer<T> {
        private final BlockingQueue<T> queue;

        public BoundedFifoBuffer(int capacity) {
            if (capacity <= 0) throw new IllegalArgumentException("вместимость должна быть больше нуля");
            this.queue = new ArrayBlockingQueue<>(capacity);
        }

        public void put(T value) throws InterruptedException {
            if (value == null) throw new IllegalArgumentException("значение не должно быть null");
            queue.put(value);
        }

        public T take() throws InterruptedException {
            return queue.take();
        }

        public int size() {
            return queue.size();
        }

        public boolean tryPut(T value) {
            if (value == null) throw new IllegalArgumentException("значение не должно быть null");
            return queue.offer(value);
        }

        public T tryTake() {
            return queue.poll();
        }

        public boolean putWithin(T value, long timeout, TimeUnit unit) throws InterruptedException {
            if (value == null) throw new IllegalArgumentException("значение не должно быть null");
            return queue.offer(value, timeout, unit);
        }

        public T takeWithin(long timeout, TimeUnit unit) throws InterruptedException {
            return queue.poll(timeout, unit);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        BoundedFifoBuffer<Integer> buffer = new BoundedFifoBuffer<>(5);

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    buffer.put(i);
                    System.out.println("PUT:  " + i + " (size=" + buffer.size() + ")");
                    Thread.sleep(300);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    int value = buffer.take();
                    System.out.println("TAKE: " + value + " (size=" + buffer.size() + ")");
                    Thread.sleep(600);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        producer.start();
        consumer.start();

        producer.join();
        consumer.join();
    }
}