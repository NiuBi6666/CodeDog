package cn.codedog.config;

import cn.codedog.service.AuditContext;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ControllerAdvice
public class AuditResponseBodyAdvice implements ResponseBodyAdvice<Object> {
    @Override public boolean supports(MethodParameter parameter,Class<? extends HttpMessageConverter<?>> converter) {
        return AuditContext.active();
    }
    @Override public Object beforeBodyWrite(Object body,MethodParameter parameter,MediaType type,
        Class<? extends HttpMessageConverter<?>> converter,ServerHttpRequest request,ServerHttpResponse response) {
        AuditContext.State state=AuditContext.current();
        if(body instanceof Map<?,?> values && values.get("error")!=null)
            AuditContext.error(String.valueOf(values.get("error")));
        if(state!=null && body!=null && !(body instanceof byte[]) && !(body instanceof SseEmitter))
            state.responseCount(count(body));
        return body;
    }
    private int count(Object body) {
        if(body instanceof Collection<?> values) return values.size();
        if(body instanceof Map<?,?> values) return values.size();
        if(body.getClass().isArray()) return Array.getLength(body);
        for(String accessor:new String[]{"total","rowCount","studentCount","count"}) {
            try {
                Method method=body.getClass().getMethod(accessor);
                Object value=method.invoke(body);
                if(value instanceof Number number) return Math.max(0,number.intValue());
            } catch(ReflectiveOperationException ignored) {
                // Only numeric count accessors are inspected; response content is never retained.
            }
        }
        return 1;
    }
}
