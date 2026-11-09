package com.redislite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.Before;
import org.junit.Test;

public class CommandHandlerTest {

    private AtomicLong now;
    private CommandHandler handler;

    @Before
    public void setUp() {
        now = new AtomicLong(0);
        handler = new CommandHandler(new KeyValueStore(now::get));
    }

    @Test
    public void pingReturnsPong() {
        assertEquals("PONG", handler.execute("PING"));
    }

    @Test
    public void setThenGet() {
        assertEquals("OK", handler.execute("SET city Delhi"));
        assertEquals("Delhi", handler.execute("GET city"));
    }

    @Test
    public void commandsAreCaseInsensitiveButKeysAreNot() {
        handler.execute("set Name abc");
        assertEquals("abc", handler.execute("get Name"));
        assertEquals("(nil)", handler.execute("get name"));
    }

    @Test
    public void getMissingKeyReturnsNil() {
        assertEquals("(nil)", handler.execute("GET nothing"));
    }

    @Test
    public void delReturnsOneThenZero() {
        handler.execute("SET a 1");
        assertEquals("(integer) 1", handler.execute("DEL a"));
        assertEquals("(integer) 0", handler.execute("DEL a"));
        assertEquals("(nil)", handler.execute("GET a"));
    }

    @Test
    public void valueCanContainSpaces() {
        handler.execute("SET greeting hello big world");
        assertEquals("hello big world", handler.execute("GET greeting"));
    }

    @Test
    public void setWithExpiryExpiresKey() {
        assertEquals("OK", handler.execute("SET token xyz EX 5"));
        assertEquals("xyz", handler.execute("GET token"));
        assertEquals("(integer) 5", handler.execute("TTL token"));

        now.addAndGet(5_000);
        assertEquals("(nil)", handler.execute("GET token"));
        assertEquals("(integer) -2", handler.execute("TTL token"));
    }

    @Test
    public void expiryOptionWorksWithSpacedValue() {
        handler.execute("SET msg hello world EX 3");
        assertEquals("hello world", handler.execute("GET msg"));
        assertEquals("(integer) 3", handler.execute("TTL msg"));
    }

    @Test
    public void invalidExpiryIsRejected() {
        assertEquals("ERR invalid expire time", handler.execute("SET k v EX abc"));
        assertEquals("ERR invalid expire time", handler.execute("SET k v EX 0"));
        assertEquals("ERR invalid expire time", handler.execute("SET k v EX -5"));
    }

    @Test
    public void ttlOfKeyWithoutExpiryIsMinusOne() {
        handler.execute("SET k v");
        assertEquals("(integer) -1", handler.execute("TTL k"));
    }

    @Test
    public void wrongArgumentCountReturnsError() {
        assertTrue(handler.execute("SET onlykey").startsWith("ERR wrong number of arguments"));
        assertTrue(handler.execute("GET").startsWith("ERR wrong number of arguments"));
        assertTrue(handler.execute("GET a b").startsWith("ERR wrong number of arguments"));
        assertTrue(handler.execute("DEL").startsWith("ERR wrong number of arguments"));
    }

    @Test
    public void unknownCommandReturnsError() {
        assertEquals("ERR unknown command 'FLY'", handler.execute("FLY away"));
    }

    @Test
    public void blankInputReturnsError() {
        assertEquals("ERR empty command", handler.execute("   "));
        assertEquals("ERR empty command", handler.execute(null));
    }
}
