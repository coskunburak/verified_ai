package com.verifiedai.visualization.api;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.visualization.application.VisualizationService;
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
public class VisualizationRequestLimitFilter extends OncePerRequestFilter {
    private final HandlerExceptionResolver resolver;
    public VisualizationRequestLimitFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) { this.resolver=resolver; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getRequestURI().matches("/api/v1/problem-sessions/[^/]+/visualizations");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
        throws IOException,ServletException {
        byte[] bytes=request.getInputStream().readNBytes(16_385);
        if(bytes.length>16_384) {
            resolver.resolveException(request,response,null,VisualizationService.error(ApiErrorCode.REQUEST_TOO_LARGE,HttpStatus.PAYLOAD_TOO_LARGE)); return;
        }
        if(bytes.length>0) {
            try {
                com.verifiedai.sharedkernel.contracts.VersionedJsonContract.parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
            } catch(IllegalArgumentException ex) {
                resolver.resolveException(request,response,null,VisualizationService.error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY)); return;
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
