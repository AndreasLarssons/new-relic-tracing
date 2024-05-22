package com.bitcoin.wallet;

import com.newrelic.api.agent.NewRelic;
import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class DebugService extends RxDebugServiceGrpc.DebugServiceImplBase {

	private static final Logger logger = LoggerFactory.getLogger(DebugService.class);

	private ExecutorService service = Executors.newFixedThreadPool(10);

	@Override
	public Single<HelloResponse> sayHello(Single<HelloRequest> request) {
		String traceId = NewRelic.getAgent().getTraceMetadata().getTraceId();
		logger.info("Gonna answer request {}", traceId);
		return request
				.flatMap(r -> Single.create((SingleOnSubscribe<HelloRequest>) emitter -> service.submit(() -> emitter.onSuccess(r))))
				.map(HelloRequest::getName)
				.map(name -> HelloResponse.newBuilder()
						.setMessage("Hello " + name)
						.build())
				.doOnSuccess(s -> {
					String traceId1 = NewRelic.getAgent().getTraceMetadata().getTraceId();
					logger.info("Answering response: {} traceId {}", s.getMessage(), traceId1);
				});
	}

	private final class LoggingTaskDecorator implements TaskDecorator {

		@Override
		public Runnable decorate(Runnable task) {
			Map<String, String> webThreadContext = MDC.getCopyOfContextMap();
			return () -> {
				try {
					MDC.setContextMap(webThreadContext);
					task.run();
				} finally {
					MDC.clear();
				}
			};
		}

	}
}
