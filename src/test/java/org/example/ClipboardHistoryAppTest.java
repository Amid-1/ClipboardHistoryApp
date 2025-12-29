package org.example;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;


class ClipboardHistoryAppTest {

    @Test
    void fifoOrder_put123_take123() throws InterruptedException {
        ClipboardHistoryApp.BoundedFifoBuffer<Integer> buffer =
                new ClipboardHistoryApp.BoundedFifoBuffer<>(10);

        buffer.put(1);
        buffer.put(2);
        buffer.put(3);

        assertEquals(1, buffer.tryTake());
        assertEquals(2, buffer.tryTake());
        assertEquals(3, buffer.tryTake());
        assertNull(buffer.tryTake());
    }

    @Test
    void capacityLimit_tryPutReturnsFalseWhenFull() {
        ClipboardHistoryApp.BoundedFifoBuffer<Integer> buffer =
                new ClipboardHistoryApp.BoundedFifoBuffer<>(2);

        assertTrue(buffer.tryPut(1));
        assertTrue(buffer.tryPut(2));
        assertFalse(buffer.tryPut(3));
        assertEquals(2, buffer.size());
    }

    @Test
    void empty_tryTakeReturnsNull() {
        ClipboardHistoryApp.BoundedFifoBuffer<Integer> buffer =
                new ClipboardHistoryApp.BoundedFifoBuffer<>(5);

        assertNull(buffer.tryTake());
        assertEquals(0, buffer.size());
    }

    @Test
    void timeouts_takeWithinReturnsNullWhenEmpty() {
        ClipboardHistoryApp.BoundedFifoBuffer<Integer> buffer =
                new ClipboardHistoryApp.BoundedFifoBuffer<>(1);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            Integer v = buffer.takeWithin(200, TimeUnit.MILLISECONDS);
            assertNull(v);
        });
    }

    @Test
    void timeouts_takeWithinReturnsValueIfProducedBeforeTimeout() {
        ClipboardHistoryApp.BoundedFifoBuffer<Integer> buffer =
                new ClipboardHistoryApp.BoundedFifoBuffer<>(1);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            Thread producer = new Thread(() -> {
                try {
                    Thread.sleep(150);
                    buffer.put(42);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "producer");

            producer.start();

            Integer v = buffer.takeWithin(1, TimeUnit.SECONDS);
            assertEquals(42, v);

            producer.join();
        });
    }

    @Test
    void timeouts_putWithinWaitsAndSucceedsIfSpaceFreesBeforeTimeout() {
        ClipboardHistoryApp.BoundedFifoBuffer<Integer> buffer =
                new ClipboardHistoryApp.BoundedFifoBuffer<>(1);

        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            buffer.put(1);

            AtomicReference<Integer> takenRef = new AtomicReference<>();
            CountDownLatch consumerStarted = new CountDownLatch(1);

            Thread consumer = new Thread(() -> {
                try {
                    consumerStarted.countDown();
                    Thread.sleep(200);
                    takenRef.set(buffer.take());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "consumer");

            consumer.start();
            assertTrue(consumerStarted.await(1, TimeUnit.SECONDS));

            boolean ok = buffer.putWithin(2, 1, TimeUnit.SECONDS);
            assertTrue(ok);

            consumer.join();

            assertNotNull(takenRef.get());
            assertEquals(1, takenRef.get());
            assertEquals(2, buffer.take());
        });
    }

    @Test
    void timeouts_putWithinReturnsFalseIfNoSpaceFreed() throws InterruptedException {
        ClipboardHistoryApp.BoundedFifoBuffer<Integer> buffer =
                new ClipboardHistoryApp.BoundedFifoBuffer<>(1);

        buffer.put(1);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            boolean ok = buffer.putWithin(2, 200, TimeUnit.MILLISECONDS);
            assertFalse(ok);
        });

        assertEquals(1, buffer.take());
    }

    @Test
    void wrapAround_capacity3_put123_take12_put45_take345() throws InterruptedException {
        ClipboardHistoryApp.BoundedFifoBuffer<Integer> buffer =
                new ClipboardHistoryApp.BoundedFifoBuffer<>(3);

        buffer.put(1);
        buffer.put(2);
        buffer.put(3);

        assertEquals(1, buffer.take());
        assertEquals(2, buffer.take());

        buffer.put(4);
        buffer.put(5);

        assertEquals(3, buffer.take());
        assertEquals(4, buffer.take());
        assertEquals(5, buffer.take());
        assertNull(buffer.tryTake());
    }

    @Test
    void stress_multiProducerMultiConsumer_noLossNoDuplicates() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            int capacity = 32;
            int producers = 4;
            int consumers = 4;
            int perProducer = 2000;
            int total = producers * perProducer;

            ClipboardHistoryApp.BoundedFifoBuffer<Integer> buffer =
                    new ClipboardHistoryApp.BoundedFifoBuffer<>(capacity);

            AtomicInteger seq = new AtomicInteger(0);
            AtomicInteger consumedCount = new AtomicInteger(0);
            AtomicBoolean duplicateFound = new AtomicBoolean(false);

            Set<Integer> consumed = ConcurrentHashMap.newKeySet(total);

            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch producersDone = new CountDownLatch(producers);

            ExecutorService pool = Executors.newFixedThreadPool(producers + consumers);

            for (int p = 0; p < producers; p++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perProducer; i++) {
                            int v = seq.incrementAndGet();
                            buffer.put(v);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        producersDone.countDown();
                    }
                });
            }

            for (int c = 0; c < consumers; c++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        while (true) {
                            int already = consumedCount.get();
                            if (already >= total) break;

                            Integer v = buffer.takeWithin(200, TimeUnit.MILLISECONDS);
                            if (v == null) {
                                if (producersDone.getCount() == 0 && buffer.size() == 0) break;
                                continue;
                            }

                            if (!consumed.add(v)) {
                                duplicateFound.set(true);
                            }
                            consumedCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            start.countDown();

            assertTrue(producersDone.await(3, TimeUnit.SECONDS),
                    "Потоки, добавляющие данные в буфер, " +
                            "не завершили работу вовремя");

            pool.shutdown();
            assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS), "Пул потоков не завершился вовремя");

            assertFalse(duplicateFound.get(), "Обнаружены дубликаты");
            assertEquals(total, consumed.size(),
                    "Обнаружена потеря данных: количество полученных элементов " +
                            "не равно количеству добавленных в буфер");
        });
    }
}
