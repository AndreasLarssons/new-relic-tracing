package com.bitcoin.wallet;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.newrelic.api.agent.Trace;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Configuration
public class Config {

	private ExecutorService threadpool(int poolsize) {
		logger.info("Starting client connection pool with fixed size={}", poolsize);
		return Executors.newFixedThreadPool(poolsize, new ThreadFactoryBuilder()
				.setNameFormat("grpc-app-client-pool" + "-%d")
				.build());
	}

	private static final Logger logger = LoggerFactory.getLogger(Config.class);

	private static final String W3C_TRACE_PARENT_HEADER = "traceparent";
	private static final String W3C_TRACE_STATE_HEADER = "tracestate";
	private static final String NEWRELIC_HEADER = "newrelic";

	@Trace(dispatcher = true)
	@Bean
	public Server server(DebugService service) throws IOException {
		String serverPort = "50051";
		logger.info(String.format("Grpc server running on port: %s", serverPort));
		NettyServerBuilder serverBuilder = NettyServerBuilder.forPort(Integer.parseInt(serverPort));
		serverBuilder.permitKeepAliveTime(20, TimeUnit.SECONDS);
		serverBuilder.permitKeepAliveWithoutCalls(true);
		serverBuilder.executor(threadpool(50));
		serverBuilder.addService(service);
		serverBuilder.addService(ProtoReflectionService.newInstance());

		serverBuilder.intercept(new Interceptor());

		Server server = serverBuilder.build();
		server.start();
		return server;
	}

	/*private ServerInterceptor getInterceptor() {
		return new ServerInterceptor() {
			@Override
			public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
				String userId = headers.get(Key.of("user_id", Metadata.ASCII_STRING_MARSHALLER));
				NewRelic.setUserId(userId);
				NewRelic.setTransactionName("grpc", "sayHello");
				String traceId = headers.get(Key.of("trace_id", Metadata.ASCII_STRING_MARSHALLER));
				Headers distributedTraceHeaders = ConcurrentHashMapHeaders.build(HeaderType.HTTP);
				distributedTraceHeaders.addHeader("trace_id", traceId);
				distributedTraceHeaders.addHeader(W3C_TRACE_PARENT_HEADER, headers.get(Key.of(W3C_TRACE_PARENT_HEADER, Metadata.ASCII_STRING_MARSHALLER)));
				distributedTraceHeaders.addHeader(W3C_TRACE_STATE_HEADER, headers.get(Key.of(W3C_TRACE_STATE_HEADER, Metadata.ASCII_STRING_MARSHALLER)));
				distributedTraceHeaders.addHeader(NEWRELIC_HEADER, headers.get(Key.of(NEWRELIC_HEADER, Metadata.ASCII_STRING_MARSHALLER)));
				NewRelic.getAgent().getTransaction().acceptDistributedTraceHeaders(TransportType.Other, distributedTraceHeaders);
				NewRelic.getAgent().getTransaction().addOutboundResponseHeaders();
				logger.info("Reading trace headers userId={} traceId={}", userId, NewRelic.getAgent().getTraceMetadata().getTraceId());

				return new SimpleForwardingServerCallListener<>(next.startCall(call, headers)) {

					@Override
					protected Listener<ReqT> delegate() {
						return super.delegate();
					}
				};
			}
		};
	}*/
}
