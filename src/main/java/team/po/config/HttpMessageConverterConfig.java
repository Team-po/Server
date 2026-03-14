package team.po.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverters;

import lombok.RequiredArgsConstructor;
import team.po.common.util.MultipartJsonHttpMessageConverter;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@RequiredArgsConstructor
public class HttpMessageConverterConfig {

	private final JsonMapper jsonMapper;

	@Bean
	public MultipartJsonHttpMessageConverter multipartJsonHttpMessageConverter() {
		return new MultipartJsonHttpMessageConverter(jsonMapper);
	}

	@Bean
	public HttpMessageConverters customHttpMessageConverters(
		MultipartJsonHttpMessageConverter multipartJsonHttpMessageConverter
	) {
		return HttpMessageConverters.forServer()
			.registerDefaults()
			.configureMessageConvertersList(converters -> converters.add(0, multipartJsonHttpMessageConverter))
			.build();
	}
}
