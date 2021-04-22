package io.github.spring.boot.common.aspect;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ControllerLogAutoConfiguration {

    @Configuration
    @ConditionalOnExpression("${controller.print:true}")
    public class ControllerAopPlus {

        @Bean
        public ControllerAop controllerAop() {
            return new ControllerAop();
        }
    }
}
