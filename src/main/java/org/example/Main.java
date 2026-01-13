package org.example;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        ClipboardHistoryApp.BoundedFifoBuffer<String> buffer =
                new ClipboardHistoryApp.BoundedFifoBuffer<>(3);

        buffer.put("A");
        buffer.put("B");
        buffer.put("C");

        System.out.println(buffer.tryPut("D")); // false, потому что буфер полный

        System.out.println(buffer.take()); // A
        System.out.println(buffer.take()); // B
        System.out.println(buffer.take()); // C

        System.out.println(buffer.tryPut("D")); // true, потому что место появилось
    }
}