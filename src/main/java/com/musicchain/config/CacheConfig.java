package com.musicchain.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Cache configuration to avoid hitting the MusicBrainz API
 * more than necessary (rate limit: 1 req/sec).
 *
 * artistSearch:      short TTL — search results can change
 * artistRecordings:  longer TTL — discographies are stable
 * recordingArtists:  longer TTL — song credits rarely change
 * artistDetail:      long TTL — artist info is very stable
 * recordingDetail:   long TTL — recording metadata is stable
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
            manager.setCaches(List.of(
                buildCache("artistSearch",      500,  30, TimeUnit.MINUTES),
                buildCache("artistRecordings",  200,   2, TimeUnit.HOURS),
                buildCache("recordingArtists",  2000,  4, TimeUnit.HOURS),
                buildCache("artistDetail",      1000, 24, TimeUnit.HOURS),
                buildCache("recordingDetail",   1000, 24, TimeUnit.HOURS),
                buildCache("songSearch",        500,  30, TimeUnit.MINUTES),
                buildCache("songSearchForArtist", 500, 30, TimeUnit.MINUTES),
                buildCache("wikidataBandMembers",       500, 24, TimeUnit.HOURS),
                buildCache("wikidataRecordings",        500,  4, TimeUnit.HOURS),
                buildCache("wikidataArtistsForRecording", 2000, 4, TimeUnit.HOURS),
                buildCache("bandMembers",               500, 24, TimeUnit.HOURS),
                buildCache("lastfmArtistSearch",        500, 30, TimeUnit.MINUTES),
                buildCache("lastfmListeners",          1000,  2, TimeUnit.HOURS),
                buildCache("lastfmPlaycount",          1000,  2, TimeUnit.HOURS)
            ));
        return manager;
    }

    private CaffeineCache buildCache(String name, int maxSize, long duration, TimeUnit unit) {
        return new CaffeineCache(name,
            Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(duration, unit)
                .build()
        );
    }
}
