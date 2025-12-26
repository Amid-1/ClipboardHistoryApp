package org.example;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ClipboardHistoryApp {

    static final class BoundedFifoBuffer<T> {
        private final Object[] elements;
        private final int capacity;

        private int head = 0;
        private int tail = 0;
        private int size = 0;

        private final ReentrantLock lock = new ReentrantLock(true);
        private final Condition notEmpty = lock.newCondition();
        private final Condition notFull = lock.newCondition();

        public BoundedFifoBuffer(int capacity) {
            if (capacity <= 0) throw new IllegalArgumentException("Вместимость буфера должна быть положительным числом");
            this.capacity = capacity;
            this.elements = new Object[capacity];
        }

        public int size() {
            lock.lock();
            try {
                return size;
            } finally {
                lock.unlock();
            }
        }

        public int capacity() {
            return capacity;
        }

        public void put(T value) throws InterruptedException {
            if (value == null) throw new IllegalArgumentException("value (значение) не должно быть null");

            lock.lockInterruptibly();
            try {
                while (size == capacity) {
                    notFull.await();
                }
                enqueue(value);
                notEmpty.signalAll();
            } finally {
                lock.unlock();
            }
        }

        public T take() throws InterruptedException {
            lock.lockInterruptibly();
            try {
                while (size == 0) {
                    notEmpty.await();
                }
                T value = dequeue();
                notFull.signalAll();
                return value;
            } finally {
                lock.unlock();
            }
        }

        public boolean tryPut(T value) {
            if (value == null) throw new IllegalArgumentException("value (значение) не должно быть null");

            lock.lock();
            try {
                if (size == capacity) return false;
                enqueue(value);
                notEmpty.signalAll();
                return true;
            } finally {
                lock.unlock();
            }
        }

        public T tryTake() {
            lock.lock();
            try {
                if (size == 0) return null;
                T value = dequeue();
                notFull.signalAll();
                return value;
            } finally {
                lock.unlock();
            }
        }

        public boolean putWithin(T value, long timeout, TimeUnit unit) throws InterruptedException {
            if (value == null) throw new IllegalArgumentException("value (значение) не должно быть null");
            if (unit == null) throw new IllegalArgumentException("unit (единица времени) не должен быть null");

            long nanos = unit.toNanos(timeout);
            lock.lockInterruptibly();
            try {
                while (size == capacity) {
                    if (nanos <= 0L) return false;
                    nanos = notFull.awaitNanos(nanos);
                }
                enqueue(value);
                notEmpty.signalAll();
                return true;
            } finally {
                lock.unlock();
            }
        }

        public T takeWithin(long timeout, TimeUnit unit) throws InterruptedException {
            if (unit == null) throw new IllegalArgumentException("unit (единица времени) не должен быть null");

            long nanos = unit.toNanos(timeout);
            lock.lockInterruptibly();
            try {
                while (size == 0) {
                    if (nanos <= 0L) return null;
                    nanos = notEmpty.awaitNanos(nanos);
                }
                T value = dequeue();
                notFull.signalAll();
                return value;
            } finally {
                lock.unlock();
            }
        }

        private void enqueue(T value) {
            elements[tail] = value;
            tail = (tail + 1) % capacity;
            size++;
        }

        @SuppressWarnings("unchecked")
        private T dequeue() {
            Object v = elements[head];
            elements[head] = null;
            head = (head + 1) % capacity;
            size--;
            return (T) v;
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