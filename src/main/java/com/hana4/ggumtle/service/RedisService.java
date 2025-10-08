package com.hana4.ggumtle.service;

import java.time.Duration;
import java.util.Set;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RedisService {
	private final RedisTemplate<String, Object> redisTemplate;

	public <T> T getValuesFromKey(String key) {
		Object value = redisTemplate.opsForValue().get(key);

		return value == null ? null : (T) value;
	}

	public void setKeyAndValue(String key, Object value, Duration timeout) {
		redisTemplate.opsForValue().set(key, value, timeout);
	}

	public void deleteKeysByPrefix(String prefix) {
		String pattern = prefix + "*";
		Set<String> keys = redisTemplate.keys(pattern);

		if (keys != null && !keys.isEmpty()) {
			redisTemplate.delete(keys);
			System.out.println("Deleted " + keys.size() + " keys with prefix: " + prefix);
		}
	}
}
