package com.ethlo.geodata;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.DoubleConsumer;

import com.google.common.util.concurrent.RateLimiter;

public class ProgressListener
{
    private final long total;
    private final AtomicLong current;
    private final DoubleConsumer consumer;
    private final RateLimiter limiter = RateLimiter.create(1);
    
    public ProgressListener(long total, DoubleConsumer consumer)
    {
        this.total = total;
        this.current = new AtomicLong();
        this.consumer = consumer;
    }
    
    public void update()
    {
        final long cur = current.incrementAndGet();
        if (limiter.tryAcquire())
        {
            consumer.accept((double)cur / total);
        }
    }
}
