package org.example;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ClipboardHistoryApp {

    public static final class BoundedFifoBuffer<T> {
        private final Object[] elements;
        private final int capacity;

        private int head = 0;
        private int tail = 0;
        private int size = 0;

        private final ReentrantLock lock = new ReentrantLock(true);
        private final Condition notEmpty = lock.newCondition();
        private final Condition notFull = lock.newCondition();

        public BoundedFifoBuffer(int capacity) {
            if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
            this.capacity = capacity;
            this.elements = new Object[capacity];
        }

        public int capacity() {
            return capacity;
        }

        public int size() {
            lock.lock();
            try {
                return size;
            } finally {
                lock.unlock();
            }
        }

        public void put(T value) throws InterruptedException {
            if (value == null) throw new NullPointerException("value");

            lock.lockInterruptibly();
            try {
                while (size == capacity) {
                    notFull.await();
                }
                enqueue(value);
                notEmpty.signal();
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
                notFull.signal();
                return value;
            } finally {
                lock.unlock();
            }
        }

        public boolean tryPut(T value) {
            if (value == null) throw new NullPointerException("value");

            if (!lock.tryLock()) return false;
            try {
                if (size == capacity) return false;
                enqueue(value);
                notEmpty.signal();
                return true;
            } finally {
                lock.unlock();
            }
        }

        public T tryTake() {
            if (!lock.tryLock()) return null;
            try {
                if (size == 0) return null;
                T value = dequeue();
                notFull.signal();
                return value;
            } finally {
                lock.unlock();
            }
        }

        public boolean putWithin(T value, long timeout, TimeUnit unit) throws InterruptedException {
            if (value == null) throw new NullPointerException("value");
            if (unit == null) throw new NullPointerException("unit");

            long nanos = unit.toNanos(timeout);
            lock.lockInterruptibly();
            try {
                while (size == capacity) {
                    if (nanos <= 0L) return false;
                    nanos = notFull.awaitNanos(nanos);
                }
                enqueue(value);
                notEmpty.signal();
                return true;
            } finally {
                lock.unlock();
            }
        }

        public T takeWithin(long timeout, TimeUnit unit) throws InterruptedException {
            if (unit == null) throw new NullPointerException("unit");

            long nanos = unit.toNanos(timeout);
            lock.lockInterruptibly();
            try {
                while (size == 0) {
                    if (nanos <= 0L) return null;
                    nanos = notEmpty.awaitNanos(nanos);
                }
                T value = dequeue();
                notFull.signal();
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
}
