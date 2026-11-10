package com.verifiedai.mistake.api;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Bounds actual bytes, including chunked requests, before JSON deserialization. */
@Component
public class MistakeDnaRequestLimitFilter extends OncePerRequestFilter {
    private final HandlerExceptionResolver resolver;
    private final io.micrometer.core.instrument.MeterRegistry metrics;
    public MistakeDnaRequestLimitFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver,io.micrometer.core.instrument.MeterRegistry metrics) { this.resolver=resolver; this.metrics=metrics; }
    private static String route(HttpServletRequest request) {
        // Servlet path is decoded by the container; encoded route letters must not bypass wire validation.
        String path=request.getServletPath(); return path==null || path.isEmpty()?request.getRequestURI():path;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !route(request).matches("/api/v1/mistakes/[^/]+/dispute");
    }
    private static ApiProblemException error(ApiErrorCode code,HttpStatus status) {
        return new ApiProblemException(status,code,"Invalid mistake dispute",false,"REVIEW");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
        throws IOException,ServletException {
        byte[] bytes=request.getInputStream().readNBytes(16_385);
        if(bytes.length>16_384) {
            metrics.counter("mistake.dna.outcome","outcome","REQUEST_TOO_LARGE").increment();
            resolver.resolveException(request,response,null,error(ApiErrorCode.REQUEST_TOO_LARGE,HttpStatus.PAYLOAD_TOO_LARGE)); return;
        }
        if(bytes.length==0) {
            resolver.resolveException(request,response,null,error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY)); return;
        }
        if(bytes.length>0) {
            try {
                String json=java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                com.verifiedai.sharedkernel.contracts.VersionedJsonContract.validate("mistake-dispute-request-v1",json);
            } catch(IllegalArgumentException | com.verifiedai.sharedkernel.error.ApiProblemException | java.nio.charset.CharacterCodingException ex) {
                metrics.counter("mistake.dna.outcome","outcome","REQUEST_VALIDATION_FAILED").increment();
                resolver.resolveException(request,response,null,error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY)); return;
            }
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var input=new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    public int read() { return input.read(); }
                    public boolean isFinished() { return input.available()==0; }
                    public boolean isReady() { return true; }
                    public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous JSON only"); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8)); }
        },response);
    }
}
