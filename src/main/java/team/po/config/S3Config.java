package team.po.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.signer.AwsS3V4Signer;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.client.config.SdkAdvancedClientOption;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

	@Bean
	public S3Client s3Client(
		@Value("${cloud.aws.credentials.access-key}") String accessKey,
		@Value("${cloud.aws.credentials.secret-key}") String secretKey,
		@Value("${cloud.aws.region.static}") String region,
		@Value("${cloud.aws.s3.endpoint:}") String endpoint,
		@Value("${cloud.aws.s3.path-style-access-enabled:true}") boolean pathStyleAccessEnabled
	) {
		S3ClientBuilder builder = S3Client.builder()
			.region(Region.of(region))
			.credentialsProvider(staticCredentialsProvider(accessKey, secretKey))
			.httpClientBuilder(UrlConnectionHttpClient.builder())
			.serviceConfiguration(s3Configuration(pathStyleAccessEnabled))
			.overrideConfiguration(s3OverrideConfiguration());

		applyEndpointOverride(builder, endpoint);

		return builder.build();
	}

	@Bean
	public S3Presigner s3Presigner(
		S3Client s3Client,
		@Value("${cloud.aws.credentials.access-key}") String accessKey,
		@Value("${cloud.aws.credentials.secret-key}") String secretKey,
		@Value("${cloud.aws.region.static}") String region,
		@Value("${cloud.aws.s3.endpoint:}") String endpoint,
		@Value("${cloud.aws.s3.path-style-access-enabled:true}") boolean pathStyleAccessEnabled
	) {
		S3Presigner.Builder builder = S3Presigner.builder()
			.region(Region.of(region))
			.credentialsProvider(staticCredentialsProvider(accessKey, secretKey))
			.serviceConfiguration(s3Configuration(pathStyleAccessEnabled))
			.s3Client(s3Client);

		applyEndpointOverride(builder, endpoint);

		return builder.build();
	}

	private StaticCredentialsProvider staticCredentialsProvider(String accessKey, String secretKey) {
		return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
	}

	private S3Configuration s3Configuration(boolean pathStyleAccessEnabled) {
		return S3Configuration.builder()
			.pathStyleAccessEnabled(pathStyleAccessEnabled)
			.build();
	}

	private ClientOverrideConfiguration s3OverrideConfiguration() {
		return ClientOverrideConfiguration.builder()
			.putAdvancedOption(SdkAdvancedClientOption.SIGNER, AwsS3V4Signer.create())
			.build();
	}

	private void applyEndpointOverride(S3ClientBuilder builder, String endpoint) {
		URI endpointUri = endpointUri(endpoint);
		if (endpointUri != null) {
			builder.endpointOverride(endpointUri);
		}
	}

	private void applyEndpointOverride(S3Presigner.Builder builder, String endpoint) {
		URI endpointUri = endpointUri(endpoint);
		if (endpointUri != null) {
			builder.endpointOverride(endpointUri);
		}
	}

	private URI endpointUri(String endpoint) {
		if (endpoint == null || endpoint.isBlank()) {
			return null;
		}

		URI uri = URI.create(endpoint);
		if (uri.getScheme() == null || uri.getHost() == null) {
			return null;
		}

		return uri;
	}
}
