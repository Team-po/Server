package team.po.common.redis;

import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

@Component
public class RedisDao {

	private final RedisTemplate<String, Object> redisTemplate;
	private final ValueOperations<String, Object> valueOperations;

	public RedisDao(RedisTemplate<String, Object> redisTemplate) {
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

	public void deleteValue(String key) {
		redisTemplate.delete(key);
	}
}
