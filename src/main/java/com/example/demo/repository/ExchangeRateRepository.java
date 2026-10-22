package com.example.demo.repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ExchangeRateRepository {
    private static final String BASE_CURRENCY = "IDR";
    private static final long CACHE_TTL_SECONDS = 1;

    private final StringRedisTemplate redis;
    private final ReentrantReadWriteLock ratesLock = new ReentrantReadWriteLock();
    private final Map<String, CachedRate> rates = new HashMap<>();

    public ExchangeRateRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public OptionalDouble getRate(String baseCurrency, String quoteCurrency) {
        String cacheKey = baseCurrency + ":" + quoteCurrency;
        ratesLock.readLock().lock();
        try {
            CachedRate cached = rates.get(cacheKey);
            if (cached != null && isFresh(cached)) {
                return OptionalDouble.of(cached.rate());
            }
        } finally {
            ratesLock.readLock().unlock();
        }
        return updateRate(baseCurrency, quoteCurrency, cacheKey);
    }

    public int convertCurrency(int price, String quoteCurrency) {
        double rate = getRate(BASE_CURRENCY, quoteCurrency)
                .orElseThrow(() -> new IllegalArgumentException("exchange rate not found"));
        return (int) (price * rate);
    }

    private OptionalDouble updateRate(String baseCurrency, String quoteCurrency, String cacheKey) {
        ratesLock.writeLock().lock();
        try {
            CachedRate current = rates.get(cacheKey);
            if (current != null && isFresh(current)) {
                return OptionalDouble.of(current.rate());
            }

            String redisKey = "fx:" + baseCurrency + ":" + quoteCurrency;
            String value = redis.opsForValue().get(redisKey);
            if (value == null) {
                if (current != null) {
                    throw new IllegalStateException(
                            "Exchange rate missing from Redis for " + baseCurrency + "/" + quoteCurrency);
                }
                return OptionalDouble.empty();
            }

            try {
                CachedRate updated = new CachedRate(Double.parseDouble(value), Instant.now());
                rates.put(cacheKey, updated);
                return OptionalDouble.of(updated.rate());
            } catch (NumberFormatException exception) {
                throw new IllegalStateException(
                        "Invalid exchange rate in Redis for " + baseCurrency + "/" + quoteCurrency,
                        exception);
            }
        } finally {
            ratesLock.writeLock().unlock();
        }
    }

    private boolean isFresh(CachedRate cached) {
        return !Instant.now().isAfter(cached.lastUpdated().plusSeconds(CACHE_TTL_SECONDS));
    }

    private record CachedRate(double rate, Instant lastUpdated) {
    }
}
