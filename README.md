# Redis-Lite

A lightweight, in-memory key-value database inspired by Redis, built from scratch in Java using plain TCP sockets.

You can store, retrieve and delete data with `SET`, `GET` and `DEL` commands, set keys to expire automatically, and connect with multiple clients at the same time.

## Features

- Client-server communication over **TCP sockets**
- **Multithreaded** server - every client is handled on a worker thread from a thread pool
- **Thread-safe storage** using `ConcurrentHashMap`
- **TTL-based key expiration** (`SET key value EX seconds`)
- Simple text protocol - works with `telnet` or `nc`, no special client needed
- Unit and integration tests with **JUnit**

## Tech Stack

| Area | Technology |
|------|------------|
| Language | Java 17 |
| Networking | `java.net.ServerSocket` / `Socket` |
| Concurrency | `ExecutorService`, `ScheduledExecutorService`, `ConcurrentHashMap` |
| Build tool | Maven |
| Testing | JUnit 4 |

## Supported Commands

| Command | Description | Example reply |
|---------|-------------|---------------|
| `PING` | Check that the server is alive | `PONG` |
| `SET key value` | Store a value | `OK` |
| `SET key value EX seconds` | Store a value that expires after N seconds | `OK` |
| `GET key` | Read a value | `value` or `(nil)` |
| `DEL key` | Delete a key | `(integer) 1` if deleted, `(integer) 0` if not found |
| `TTL key` | Seconds left before expiry | `(integer) 7` |
| `QUIT` | Close the connection | `OK` |

`TTL` returns `-1` if the key has no expiry and `-2` if the key does not exist (same as Redis).
Commands are case-insensitive. Keys are case-sensitive.

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+

### Build and run

```bash
git clone https://github.com/<your-username>/redis-lite.git
cd redis-lite

mvn clean package
java -jar target/redis-lite-1.0.0.jar          # starts on port 6380
java -jar target/redis-lite-1.0.0.jar 7000     # or choose your own port
```

### Connect to it

Open a second terminal and connect with `telnet` or `nc`:

```bash
nc localhost 6380
```

Example session:

```
PING
PONG
SET name alice
OK
GET name
alice
SET otp 4821 EX 10
OK
TTL otp
(integer) 10
GET otp
(nil)              <- after 10 seconds
DEL name
(integer) 1
GET name
(nil)
QUIT
OK
```

## Running the Tests

```bash
mvn test
```

The tests cover:

- `KeyValueStoreTest` - set/get/delete, TTL expiry (using a fake clock, no `sleep`), concurrent writes
- `CommandHandlerTest` - command parsing, error messages, `EX` option
- `RedisLiteServerTest` - a real server on a free port, with multiple clients connecting at once

## How It Works

```
                    +--------------------------------------------+
  Client 1 ---+     |  RedisLiteServer                           |
  Client 2 ---+---> |   accept loop --> thread pool (100)        |
  Client 3 ---+     |                     |                      |
                    |               ClientHandler (per client)   |
                    |                     |                      |
                    |               CommandHandler (parse)       |
                    |                     |                      |
                    |               KeyValueStore                |
                    |               (ConcurrentHashMap)          |
                    |                     ^                      |
                    |   expiry cleaner thread (every 1 second)   |
                    +--------------------------------------------+
```

1. `RedisLiteServer` listens on a port. Its accept loop gives every new connection to a worker thread.
2. `ClientHandler` reads one line at a time from the client and writes back one reply line.
3. `CommandHandler` splits the line into a command and arguments and calls the store.
4. `KeyValueStore` keeps entries in a `ConcurrentHashMap`, so many threads can read and write safely without a global lock.
5. **Expiration** happens in two ways: a key is removed as soon as it is read after its time is up (lazy), and a background thread removes expired keys every second so unused keys do not stay in memory (active).

## Project Structure

```
redis-lite/
├── pom.xml
├── README.md
└── src
    ├── main/java/com/redislite
    │   ├── RedisLiteServer.java     # TCP server, accept loop, thread pool
    │   ├── ClientHandler.java       # serves one connected client
    │   ├── CommandHandler.java      # parses and runs commands
    │   └── KeyValueStore.java       # thread-safe storage with TTL
    └── test/java/com/redislite
        ├── KeyValueStoreTest.java
        ├── CommandHandlerTest.java
        └── RedisLiteServerTest.java
```

## Limitations

This is a learning project, not a production database.

- Data is stored only in memory and is lost when the server stops
- Uses a simple line-based text protocol, not the real Redis (RESP) protocol, so the official `redis-cli` will not work
- Values cannot contain line breaks, and repeated spaces inside a value are collapsed into one
- Up to 100 clients are served at the same time; extra connections wait until a worker is free

## Possible Improvements

- Persistence (snapshot to disk)
- More commands (`EXISTS`, `INCR`, `KEYS`)
- Support for the RESP protocol so `redis-cli` can connect

## License

MIT
