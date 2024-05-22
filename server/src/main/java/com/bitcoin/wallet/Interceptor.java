package com.bitcoin.wallet;

import com.newrelic.api.agent.ConcurrentHashMapHeaders;
import com.newrelic.api.agent.HeaderType;
import com.newrelic.api.agent.Headers;
import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.Trace;
import com.newrelic.api.agent.TransportType;
import io.grpc.Metadata;
import io.grpc.Metadata.Key;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Interceptor implements ServerInterceptor {

	private static final Logger logger = LoggerFactory.getLogger(Interceptor.class);

	private static final String W3C_TRACE_PARENT_HEADER = "traceparent";
	private static final String W3C_TRACE_STATE_HEADER = "tracestate";
	private static final String NEWRELIC_HEADER = "newrelic";

	@Trace(dispatcher = true)
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
		return next.startCall(call, headers);
	}
}
