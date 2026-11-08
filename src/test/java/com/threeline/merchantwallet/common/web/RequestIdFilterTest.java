package com.threeline.merchantwallet.common.web;

import com.threeline.merchantwallet.common.context.RequestContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void reusesSafeIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "client-req_123.abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInsideChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seenInsideChain.set(RequestContext.requestId()));

        assertThat(seenInsideChain.get()).isEqualTo("client-req_123.abc");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("client-req_123.abc");
    }

    @Test
    void replacesUnsafeIncomingId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "abc\nFAKE LOG LINE");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader(RequestIdFilter.HEADER))
                .doesNotContain("\n")
                .matches("[0-9a-f-]{36}");
    }

    @Test
    void generatesIdWhenMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> { });
        assertThat(response.getHeader(RequestIdFilter.HEADER)).matches("[0-9a-f-]{36}");
    }

    @Test
    void clearsContextAfterRequest_soPooledThreadsDoNotLeakIdentity() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> RequestContext.setActor("merchant:abc"));

        assertThat(RequestContext.requestId()).isNull();
        assertThat(RequestContext.actor()).isEqualTo(RequestContext.ANONYMOUS);
    }
}
