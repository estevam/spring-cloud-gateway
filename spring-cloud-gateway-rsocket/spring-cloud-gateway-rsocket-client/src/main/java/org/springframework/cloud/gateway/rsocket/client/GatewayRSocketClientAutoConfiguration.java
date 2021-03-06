/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gateway.rsocket.client;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.function.Supplier;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.rsocket.RSocket;
import io.rsocket.micrometer.MicrometerRSocketInterceptor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration;
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.rsocket.common.metadata.RouteSetup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnProperty(name = "spring.cloud.gateway.rsocket.enabled",
		matchIfMissing = true)
@EnableConfigurationProperties
@ConditionalOnClass({ RSocket.class, RSocketRequester.class })
@AutoConfigureAfter(RSocketStrategiesAutoConfiguration.class)
@AutoConfigureBefore(RSocketRequesterAutoConfiguration.class)
public class GatewayRSocketClientAutoConfiguration {

	/**
	 * Name of client id generator bean.
	 */
	public static final String RSOCKET_CLIENT_ID_GENERATOR_NAME = "rsocketClientIdGenerator";

	private final RSocketMessageHandler messageHandler;

	private final SecureRandom secureRandom = new SecureRandom();

	public GatewayRSocketClientAutoConfiguration(RSocketMessageHandler handler) {
		messageHandler = handler;
	}

	@Bean
	@Scope("prototype")
	@ConditionalOnMissingBean
	public RSocketRequester.Builder gatewayRSocketRequesterBuilder(
			RSocketStrategies strategies, ClientProperties properties,
			MeterRegistry meterRegistry) {
		RouteSetup.Builder routeSetup = RouteSetup.of(properties.getRouteId(),
				properties.getServiceName());
		properties.getTags().forEach(routeSetup::with);
		properties.getCustomTags().forEach(routeSetup::with);

		MicrometerRSocketInterceptor interceptor = new MicrometerRSocketInterceptor(
				meterRegistry, Tag.of("servicename", properties.getServiceName()));

		return RSocketRequester.builder()
				.setupMetadata(routeSetup.build(), RouteSetup.ROUTE_SETUP_MIME_TYPE)
				.rsocketStrategies(strategies).rsocketFactory(
						rsocketFactory -> rsocketFactory.addRequesterPlugin(interceptor)
								.acceptor(messageHandler.responder()));
	}

	@Bean
	public BrokerClient brokerClient(RSocketRequester.Builder builder,
			ClientProperties properties) {
		return new BrokerClient(properties, builder);
	}

	@Bean(name = RSOCKET_CLIENT_ID_GENERATOR_NAME)
	@ConditionalOnMissingBean(name = RSOCKET_CLIENT_ID_GENERATOR_NAME)
	public Supplier<BigInteger> rsocketClientIdGenerator() {
		return () -> {
			byte[] bytes = new byte[16];
			secureRandom.nextBytes(bytes);
			return new BigInteger(bytes);
		};
	}

	@Bean
	public ClientProperties clientProperties(
			@Qualifier(RSOCKET_CLIENT_ID_GENERATOR_NAME) Supplier<BigInteger> clientIdGenerator) {
		ClientProperties clientProperties = new ClientProperties();
		clientProperties.setRouteId(clientIdGenerator.get());
		return clientProperties;
	}

}
