package com.servpp.sdk.editor;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class BoundedLspInputTest {
    @Test public void preservesMultipleFramesAndUtf8Bytes() throws Exception {
        byte[] body = "{\"text\":\"中文😀\"}".getBytes(StandardCharsets.UTF_8);
        String header = "Content-Length: " + body.length + "\r\nContent-Type: application/vscode-jsonrpc; charset=utf-8\r\n\r\n";
        var output = new java.io.ByteArrayOutputStream();
        output.write(header.getBytes(StandardCharsets.US_ASCII));
        output.write(body);
        output.write("Content-Length: 2\r\n\r\n{}".getBytes(StandardCharsets.US_ASCII));
        byte[] bytes = output.toByteArray();
        assertArrayEquals(bytes, stream(bytes).readAllBytes());
    }

    @Test public void rejectsInvalidOversizedAndDuplicateLengthsBeforeBodyAllocation() {
        for (String header : new String[]{"Content-Length: 8388609\r\n\r\n", "Content-Length: -1\r\n\r\n",
                "Content-Length: 999999999999\r\n\r\n", "Content-Length: 2\r\nContent-Length: 2\r\n\r\n",
                "Unknown: 2\r\n\r\n", "Content-Length: 0\r\n\r\n", "X".repeat(8193)})
            assertThrows(header.substring(0, Math.min(header.length(), 60)), IOException.class,
                    () -> stream(header.getBytes(StandardCharsets.US_ASCII)).readAllBytes());
    }

    @Test public void rejectsTruncatedFramesAndHandlesSingleByteReads() throws Exception {
        assertThrows(IOException.class, () -> stream("Content-Length: 2\r\n\r\n{".getBytes(StandardCharsets.US_ASCII)).readAllBytes());
        assertThrows(IOException.class, () -> stream("Content-Length: 2".getBytes(StandardCharsets.US_ASCII)).readAllBytes());
        try (var input = stream("Content-Length: 2\r\n\r\n{}".getBytes(StandardCharsets.US_ASCII))) {
            int bytes = 0;
            while (input.read() != -1) bytes++;
            assertEquals(23, bytes);
        }
    }

    private static BoundedLspInput stream(byte[] bytes) { return new BoundedLspInput(new ByteArrayInputStream(bytes)); }
}
