package org.example;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * ClipboardHistoryApp (single-file)
 *
 * Требование:
 * - помещать данные в свой буфер
 * - отдавать данные, соблюдая очередность (FIFO)
 *
 * Реализация:
 * - внутри ArrayBlockingQueue => FIFO + потокобезопасность
 *
 * Запуск:
 *   javac ClipboardHistoryApp.java
 *   java ClipboardHistoryApp
 */
public class ClipboardHistoryApp {

    /**
     * FIFO-буфер фиксированной емкости.
     * Базовая версия: блокирующие операции put/take.
     */
    static final class BoundedFifoBuffer<T> {
        private final BlockingQueue<T> queue;

        public BoundedFifoBuffer(int capacity) {
            if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
            this.queue = new ArrayBlockingQueue<>(capacity);
        }

        /** Положить элемент. Блокируется, если буфер заполнен. */
        public void put(T value) throws InterruptedException {
            if (value == null) throw new IllegalArgumentException("value must not be null");
            queue.put(value);
        }

        /** Взять элемент. Блокируется, если буфер пуст. */
        public T take() throws InterruptedException {
            return queue.take();
        }

        public int size() {
            return queue.size();
        }

        /** Положить элемент без блокировки. Вернет false, если буфер заполнен. */
        public boolean tryPut(T value) {
            if (value == null) throw new IllegalArgumentException("value must not be null");
            return queue.offer(value);
        }

        /** Взять элемент без блокировки. Вернет null, если буфер пуст. */
        public T tryTake() {
            return queue.poll();
        }

        /** Положить элемент с таймаутом. Вернет false, если не удалось за время timeout. */
        public boolean putWithin(T value, long timeout, TimeUnit unit) throws InterruptedException {
            if (value == null) throw new IllegalArgumentException("value must not be null");
            return queue.offer(value, timeout, unit);
        }

        /** Взять элемент с таймаутом. Вернет null, если не удалось за время timeout. */
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