package com.bitcoin.wallet;

import com.newrelic.api.agent.ConcurrentHashMapHeaders;
import com.newrelic.api.agent.HeaderType;
import com.newrelic.api.agent.Headers;
import com.newrelic.api.agent.NewRelic;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.MethodDescriptor;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SayService extends RxCallerServiceGrpc.CallerServiceImplBase {

	private static final Logger logger = LoggerFactory.getLogger(SayService.class);

	private ManagedChannel channel;
	private RxDebugServiceGrpc.RxDebugServiceStub stub;

	private static final String W3C_TRACE_PARENT_HEADER = "traceparent";
	private static final String W3C_TRACE_STATE_HEADER = "tracestate";
	private static final String NEWRELIC_HEADER = "newrelic";


	public SayService() {
		channel = ManagedChannelBuilder.forAddress("localhost", 50051)
				.intercept(new ClientInterceptor() {
					@Override
					public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
						return new SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
							@Override
							public void start(Listener<RespT> responseListener, Metadata headers) {
								Headers distributedTraceHeaders = ConcurrentHashMapHeaders.build(HeaderType.HTTP);
								NewRelic.getAgent().getTransaction().insertDistributedTraceHeaders(distributedTraceHeaders);
								String traceId = NewRelic.getAgent().getTraceMetadata().getTraceId();
								String userId = "SOME_ID_IS_SET";
								NewRelic.setUserId(userId);
								logger.info("Setting user id {}", traceId);
								Metadata metadata = new Metadata();
								distributedTraceHeaders.addHeader("trace_id", traceId);
								distributedTraceHeaders.addHeader("user_id", userId);
								metadata.put(Key.of("user_id", Metadata.ASCII_STRING_MARSHALLER), userId);
								metadata.put(Key.of("trace_id", Metadata.ASCII_STRING_MARSHALLER), traceId);
								metadata.put(Key.of(W3C_TRACE_PARENT_HEADER, Metadata.ASCII_STRING_MARSHALLER), distributedTraceHeaders.getHeader(W3C_TRACE_PARENT_HEADER));
								metadata.put(Key.of(W3C_TRACE_STATE_HEADER, Metadata.ASCII_STRING_MARSHALLER), distributedTraceHeaders.getHeader(W3C_TRACE_STATE_HEADER));
								metadata.put(Key.of(NEWRELIC_HEADER, Metadata.ASCII_STRING_MARSHALLER), distributedTraceHeaders.getHeader(NEWRELIC_HEADER));
								headers.merge(metadata);
								logger.info("Adding trace id {} to headers", traceId);
								super.start(responseListener, headers);
							}
						};
					}
				})
				.usePlaintext()
				.build();
		stub = RxDebugServiceGrpc.newRxStub(channel);
	}

	@Override
	public Single<SayResponse> say(Single<SayRequest> request) {
		String t = NewRelic.getAgent().getTraceMetadata().getTraceId();
		logger.info("Received request with traceId={}", t);
		return request
				.flatMap(req -> stub.sayHello(HelloRequest.newBuilder()
						.setName(req.getName())
						.build()))
				.map(HelloResponse::getMessage)
				.map(m -> SayResponse.newBuilder().setMessage(m).build())
				.doOnSuccess(s -> {
					String traceId1 = NewRelic.getAgent().getTraceMetadata().getTraceId();
					logger.info("Received response: {} {}", s.getMessage(), traceId1);
				});
	}
}
