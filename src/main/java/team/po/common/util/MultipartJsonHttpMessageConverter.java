package team.po.common.util;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.ResolvableType;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;

import tools.jackson.databind.json.JsonMapper;

public class MultipartJsonHttpMessageConverter extends JacksonJsonHttpMessageConverter {

	public MultipartJsonHttpMessageConverter(JsonMapper jsonMapper) {
		super(jsonMapper);

		List<MediaType> supportedMediaTypes = new ArrayList<>(getSupportedMediaTypes());
		supportedMediaTypes.add(MediaType.APPLICATION_OCTET_STREAM);
		setSupportedMediaTypes(supportedMediaTypes);
	}

	// octet-stream 읽기 전용으로 사용하고, 쓰기에는 기본 컨버터를 유지한다.
	@Override
	public boolean canWrite(Class<?> clazz, MediaType mediaType) {
		return false;
	}

	@Override
	public boolean canWrite(ResolvableType type, Class<?> valueClass, MediaType mediaType) {
		return false;
	}
}
