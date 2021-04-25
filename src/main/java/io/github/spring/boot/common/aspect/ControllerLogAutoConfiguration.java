package io.github.spring.boot.common.aspect;

import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Configuration
public class ControllerLogAutoConfiguration {

    @Configuration
    @ConditionalOnExpression("${controller.print:false}")
    public class ControllerAopPlus {

        @Bean
        public ControllerAop controllerAop() {
            return new ControllerAop();
        }
    }


    @Order(1)//设置该类在spring容器中的加载顺序
    @Aspect
    @Configuration
    public class HttpServletRequestReplacedFilter implements Filter {

        @Override
        public void destroy() {
        }

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
            ServletRequest requestWrapper = null;
            if(request instanceof HttpServletRequest) {
                requestWrapper = new ContentCachingRequestWrapper((HttpServletRequest) request);
            }
            //获取请求中的流，将取出来的字符串，再次转换成流，然后把它放入到新request对象中。
            // 在chain.doFiler方法中传递新的request对象
            if(requestWrapper == null) {
                chain.doFilter(request, response);
            } else {
                chain.doFilter(requestWrapper, response);
            }
        }

        @Override
        public void init(FilterConfig arg0) {
        }
    }
}
