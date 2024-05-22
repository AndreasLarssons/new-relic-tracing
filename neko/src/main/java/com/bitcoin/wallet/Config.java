package com.bitcoin.wallet;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
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

	@Bean
	public Server server(SayService service) throws IOException {
		String serverPort = "50052";
		logger.info(String.format("Grpc server running on port: %s", serverPort));
		NettyServerBuilder serverBuilder = NettyServerBuilder.forPort(Integer.parseInt(serverPort));
		serverBuilder.permitKeepAliveTime(20, TimeUnit.SECONDS);
		serverBuilder.permitKeepAliveWithoutCalls(true);
		serverBuilder.executor(threadpool(50));
		serverBuilder.addService(service);
		serverBuilder.addService(ProtoReflectionService.newInstance());

		Server server = serverBuilder.build();
		server.start();
		return server;
	}
}
