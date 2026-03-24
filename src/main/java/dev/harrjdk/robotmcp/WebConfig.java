package dev.harrjdk.robotmcp;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ActiveRequestTracker activeRequestTracker;

    public WebConfig(ActiveRequestTracker activeRequestTracker) {
        this.activeRequestTracker = activeRequestTracker;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(activeRequestTracker);
    }
}
