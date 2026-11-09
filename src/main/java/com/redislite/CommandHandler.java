package com.redislite;

import java.util.Arrays;

/**
 * Parses one line of text and runs it against the store.
 * Kept separate from the networking code so it can be unit tested easily.
 *
 * Supported commands:
 *   PING
 *   SET key value [EX seconds]
 *   GET key
 *   DEL key
 *   TTL key
 */
public class CommandHandler {

    private final KeyValueStore store;

    public CommandHandler(KeyValueStore store) {
        this.store = store;
    }

    public String execute(String line) {
        if (line == null || line.isBlank()) {
            return "ERR empty command";
        }

        String[] parts = line.trim().split("\\s+");
        String command = parts[0].toUpperCase();

        switch (command) {
            case "PING":
                return "PONG";
            case "SET":
                return handleSet(parts);
            case "GET":
                return handleGet(parts);
            case "DEL":
                return handleDel(parts);
            case "TTL":
                return handleTtl(parts);
            default:
                return "ERR unknown command '" + parts[0] + "'";
        }
    }

    private String handleSet(String[] parts) {
        if (parts.length < 3) {
            return "ERR wrong number of arguments for 'SET' (usage: SET key value [EX seconds])";
        }

        String key = parts[1];
        int valueEnd = parts.length;
        long ttlSeconds = 0;

        // SET key value EX 10  ->  the last two tokens are the expiry option
        if (parts.length >= 5 && parts[parts.length - 2].equalsIgnoreCase("EX")) {
            try {
                ttlSeconds = Long.parseLong(parts[parts.length - 1]);
            } catch (NumberFormatException e) {
                return "ERR invalid expire time";
            }
            if (ttlSeconds <= 0) {
                return "ERR invalid expire time";
            }
            valueEnd = parts.length - 2;
        }

        String value = String.join(" ", Arrays.copyOfRange(parts, 2, valueEnd));
        store.set(key, value, ttlSeconds);
        return "OK";
    }

    private String handleGet(String[] parts) {
        if (parts.length != 2) {
            return "ERR wrong number of arguments for 'GET' (usage: GET key)";
        }
        String value = store.get(parts[1]);
        return value == null ? "(nil)" : value;
    }

    private String handleDel(String[] parts) {
        if (parts.length != 2) {
            return "ERR wrong number of arguments for 'DEL' (usage: DEL key)";
        }
        return store.delete(parts[1]) ? "(integer) 1" : "(integer) 0";
    }

    private String handleTtl(String[] parts) {
        if (parts.length != 2) {
            return "ERR wrong number of arguments for 'TTL' (usage: TTL key)";
        }
        return "(integer) " + store.ttl(parts[1]);
    }
}
