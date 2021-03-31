package com.mybatis.spring.boot.autoconfigure;


import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.List;


@Configuration
@ConditionalOnBean(SqlSessionFactory.class)
public class MybatisSqlPrintAutoConfiguration {

    @Autowired
    private List<SqlSessionFactory> sqlSessionFactoryList;

    /**
     * 兼容一下 PageHelper，让拦截器在最后一个处理 {@literal https://github.com/pagehelper/pagehelper-spring-boot}
     * 或者通过原生的进行处理
     */
    @Configuration
    @ConditionalOnExpression("${mybatis.print:true}")
    public class SupportPageHelper {

        @PostConstruct
        public void addPrintInterceptor() {
            MybatisSqlCompletePrintInterceptor printInterceptor = new MybatisSqlCompletePrintInterceptor();
            for (SqlSessionFactory sqlSessionFactory : sqlSessionFactoryList) {
                sqlSessionFactory.getConfiguration().addInterceptor(printInterceptor);
            }
        }
    }

}
