package org.example;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;


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
        assertNull(buffer.tryTake()); // пусто
    }

    @Test
    void capacityLimit_tryPutReturnsFalseWhenFull() {
        ClipboardHistoryApp.BoundedFifoBuffer<Integer> buffer =
                new ClipboardHistoryApp.BoundedFifoBuffer<>(2);

        assertTrue(buffer.tryPut(1));
        assertTrue(buffer.tryPut(2));
        assertFalse(buffer.tryPut(3)); // переполнение
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
            buffer.put(1); // буфер заполнен

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

        buffer.put(1); // заполнено

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            boolean ok = buffer.putWithin(2, 200, TimeUnit.MILLISECONDS);
            assertFalse(ok);
        });

        assertEquals(1, buffer.take());
    }
}
