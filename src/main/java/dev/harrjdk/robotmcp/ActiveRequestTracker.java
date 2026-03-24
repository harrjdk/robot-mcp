package dev.harrjdk.robotmcp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ActiveRequestTracker implements HandlerInterceptor {

    private final AtomicInteger activeRequests = new AtomicInteger(0);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        activeRequests.incrementAndGet();
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        activeRequests.decrementAndGet();
    }

    public int getActiveCount() {
        return activeRequests.get();
    }
}
