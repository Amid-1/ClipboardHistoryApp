# ClipboardHistoryApp

Реализовал потокобезопасный ограниченный FIFO-буфер (bounded queue) с блокирующими операциями и вариантами с таймаутом.

Примечание: буфер реализовал вручную (кольцевой массив + ReentrantLock/Condition), без использования готовых 
реализаций очередей из `java.util.concurrent` (например, `ArrayBlockingQueue`), чтобы явно показать логику работы буфера.

## Требования
- JDK 21 или выше


## Запуск тестов

### Windows (CMD/PowerShell)
.\mvnw.cmd test

### Linux/macOS
./mvnw test