package team.po.common.redis;

import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

@Component
public class RedisService {

	private final RedisTemplate<String, Object> redisTemplate;
	private final ValueOperations<String, Object> valueOperations;

	public RedisService(RedisTemplate<String, Object> redisTemplate) {
		this.redisTemplate = redisTemplate;
		this.valueOperations = redisTemplate.opsForValue();
	}

	public void setValue(String key, Object value) {
		valueOperations.set(key, value);
	}

	public void setValue(String key, Object value, Duration timeout) {
		valueOperations.set(key, value, timeout);
	}

	public Object getValue(String key) {
		return valueOperations.get(key);
	}

	public String getStringValue(String key) {
		Object value = valueOperations.get(key);
		if (value == null) {
			return null;
		}

		return value.toString();
	}

	public Object getAndDeleteValue(String key) {
		return valueOperations.getAndDelete(key);
	}

	public String getAndDeleteStringValue(String key) {
		Object value = valueOperations.getAndDelete(key);
		if (value == null) {
			return null;
		}

		return value.toString();
	}

	public Long incrementValue(String key) {
		return valueOperations.increment(key);
	}

	public void expire(String key, Duration timeout) {
		redisTemplate.expire(key, timeout);
	}

	public void deleteValue(String key) {
		redisTemplate.delete(key);
	}
}
