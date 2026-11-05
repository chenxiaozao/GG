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
    @Test void structuredAnswerIsParsedThroughTransport() throws Exception {
        var mapper = new ObjectMapper();
        String content = mapper.writeValueAsString(java.util.Map.of("answer", "虚构推荐说明", "recommendedProductIds", List.of(1)));
        String body = mapper.writeValueAsString(java.util.Map.of("choices", List.of(java.util.Map.of("message", java.util.Map.of("content", content)))));
        var reply = withResponse(200, body).chat("虚构问题", List.of(), List.of());
        assertEquals("虚构推荐说明", reply.answer()); assertEquals(List.of(1L), reply.productIds());
    }
}
