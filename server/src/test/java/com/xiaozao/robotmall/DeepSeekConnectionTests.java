package com.xiaozao.robotmall;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.http.*;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozao.robotmall.common.BizException;
import com.xiaozao.robotmall.service.DeepSeekAdvisorService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** No real credentials or external requests: inject a mock transport for failure cases. */
class DeepSeekConnectionTests {
    @SuppressWarnings("unchecked")
    private DeepSeekAdvisorService withResponse(int status, String body) throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
        DeepSeekAdvisorService service = new DeepSeekAdvisorService(new ObjectMapper());
        ReflectionTestUtils.setField(service, "apiKey", "dummy-test-only");
        ReflectionTestUtils.setField(service, "baseUrl", "https://example.invalid");
        ReflectionTestUtils.setField(service, "model", "test-model");
        ReflectionTestUtils.setField(service, "client", client);
        return service;
    }
    @Test void invalidKeyRateLimitAndProviderFailureHaveReadableErrors() throws Exception {
        for (int status : new int[] {401, 429, 503}) {
            var service = withResponse(status, "provider response must not be exposed");
            var error = assertThrows(BizException.class, () -> service.chat("虚构问题", List.of(), List.of()));
            assertTrue(error.getMessage().contains("HTTP " + status));
            assertFalse(error.getMessage().contains("provider response"));
        }
    }
    @Test void timeoutAndNetworkFailureAreHandled() throws Exception {
        for (Exception failure : new Exception[] {new HttpTimeoutException("timed out"), new java.io.IOException("unavailable")}) {
            var service = withResponse(200, "{}");
            HttpClient client = (HttpClient) ReflectionTestUtils.getField(service, "client");
            when(client.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenThrow(failure);
            var error = assertThrows(BizException.class, () -> service.chat("虚构问题", List.of(), List.of()));
            assertTrue(error.getMessage().contains(failure instanceof HttpTimeoutException ? "超时" : "连接失败"));
        }
    }
    @Test void emptyAndMalformedProviderAnswersDoNotBecomeSuccessfulMessages() throws Exception {
        for (String body : new String[] {"{}", "{\"choices\":[{\"message\":{\"content\":\"bad json\"}}]}"}) {
            var service = withResponse(200, body);
            assertThrows(BizException.class, () -> service.chat("虚构问题", List.of(), List.of()));
        }
    }
    @Test void promptRequiresEvidenceForPetHairAndAreaClaims() throws Exception {
        var mapper=new ObjectMapper();String answer=mapper.writeValueAsString(java.util.Map.of("answer","测试回复","recommendedProductIds",List.of()));
        var service=withResponse(200,mapper.writeValueAsString(java.util.Map.of("choices",List.of(java.util.Map.of("message",java.util.Map.of("content",answer))))));
        service.chat("虚构选购问题",List.of(),List.of());
        HttpClient client=(HttpClient)ReflectionTestUtils.getField(service,"client");
        var captured=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(captured.capture(),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        var bytes=new java.io.ByteArrayOutputStream();var done=new java.util.concurrent.CompletableFuture<String>();
        captured.getValue().bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>(){
            public void onSubscribe(java.util.concurrent.Flow.Subscription s){s.request(Long.MAX_VALUE);}
            public void onNext(java.nio.ByteBuffer b){byte[] a=new byte[b.remaining()];b.get(a);bytes.writeBytes(a);}
            public void onError(Throwable t){done.completeExceptionally(t);}
            public void onComplete(){done.complete(bytes.toString(java.nio.charset.StandardCharsets.UTF_8));}
        });
        String system=mapper.readTree(done.get(2,java.util.concurrent.TimeUnit.SECONDS)).path("messages").get(0).path("content").asText();
        assertTrue(system.contains("禁止仅按标称吸力"));assertTrue(system.contains("禁止仅凭续航分钟数"));assertTrue(system.contains("推测必须标为待核实"));
    }
    @Test void structuredAnswerIsParsedThroughTransport() throws Exception {
        var mapper = new ObjectMapper();
        String content = mapper.writeValueAsString(java.util.Map.of("answer", "虚构推荐说明", "recommendedProductIds", List.of(1)));
        String body = mapper.writeValueAsString(java.util.Map.of("choices", List.of(java.util.Map.of("message", java.util.Map.of("content", content)))));
        var reply = withResponse(200, body).chat("虚构问题", List.of(), List.of());
        assertEquals("虚构推荐说明", reply.answer()); assertEquals(List.of(1L), reply.productIds());
    }
}
