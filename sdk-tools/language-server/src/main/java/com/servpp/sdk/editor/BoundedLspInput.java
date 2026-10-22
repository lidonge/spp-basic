package com.servpp.sdk.editor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class BoundedLspInput extends InputStream {
    private static final int MAX_HEADER = 8192;
    private static final int MAX_BODY = 8 * 1024 * 1024;
    private final InputStream input;
    private byte[] header = new byte[0];
    private int headerOffset;
    private int remaining;

    public BoundedLspInput(InputStream input) { this.input = input; }

    @Override public int read() throws IOException {
        byte[] single = new byte[1];
        return read(single, 0, 1) == -1 ? -1 : Byte.toUnsignedInt(single[0]);
    }

    @Override public int read(byte[] destination, int offset, int length) throws IOException {
        java.util.Objects.checkFromIndexSize(offset, length, destination.length);
        if (length == 0) return 0;
        if (headerOffset < header.length) {
            int count = Math.min(length, header.length - headerOffset);
            System.arraycopy(header, headerOffset, destination, offset, count);
            headerOffset += count;
            return count;
        }
        if (remaining > 0) {
            int count = input.read(destination, offset, Math.min(length, remaining));
            if (count < 0) throw new IOException("Incomplete LSP message.");
            remaining -= count;
            return count;
        }
        var bytes = new ByteArrayOutputStream();
        while (bytes.size() < MAX_HEADER) {
            int value = input.read();
            if (value < 0) {
                if (bytes.size() == 0) return -1;
                throw new IOException("Incomplete LSP header.");
            }
            bytes.write(value);
            if (value != '\n') continue;
            byte[] candidate = bytes.toByteArray();
            int size = candidate.length;
            if (size >= 4 && candidate[size - 4] == '\r' && candidate[size - 3] == '\n' && candidate[size - 2] == '\r') {
                int lengthHeaders = 0;
                for (String line : new String(candidate, StandardCharsets.US_ASCII).split("\r\n")) {
                    if (!line.regionMatches(true, 0, "Content-Length:", 0, 15)) continue;
                    lengthHeaders++;
                    String number = line.substring(15).strip();
                    if (!number.matches("[0-9]{1,8}")) throw new IOException("Invalid LSP content length.");
                    remaining = Integer.parseInt(number);
                }
                if (lengthHeaders != 1 || remaining <= 0 || remaining > MAX_BODY)
                    throw new IOException("LSP message exceeds framing limits.");
                header = candidate;
                headerOffset = 0;
                return read(destination, offset, length);
            }
        }
        throw new IOException("LSP header exceeds framing limits.");
    }

    @Override public void close() throws IOException { input.close(); }
}
