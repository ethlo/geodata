package com.ethlo.geodata;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

import com.google.common.util.concurrent.RateLimiter;

public class ProgressListener
{
    private final AtomicLong current;
    private final LongConsumer consumer;
    private final RateLimiter limiter = RateLimiter.create(1);
    
    public ProgressListener(LongConsumer consumer)
    {
        this.current = new AtomicLong();
        this.consumer = consumer;
    }
    
    public void update()
    {
        final long cur = current.incrementAndGet();
        if (limiter.tryAcquire())
        {
            consumer.accept(cur);
        }
    }
}
