package com.servpp.service.serial;

/**
 * A {@link SerialEngine} that allocates on {@link SerialRequest#connection()}, so the allocation commits and rolls
 * back with the business transaction and leaves no gap. Only such engines may serve a {@code gapless} rule.
 */
public interface GaplessSerialEngine extends SerialEngine { }
