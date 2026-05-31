package io.github.jiangyuan.transaction.config;

import io.github.jiangyuan.transaction.aspect.TransactionAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import javax.sql.DataSource;

/**
 * 事务自动配置类
 * <p>
 * 当 classpath 中存在 DataSource 且 {@code jiangyuan-transaction.enabled=true}（默认 true）
 * 时自动生效。注册事务切面，拦截 {@code @JTransaction} 注解并管理数据库事务。
 * </p>
 */
@AutoConfiguration
@EnableConfigurationProperties(TransactionProperties.class)
@EnableAspectJAutoProxy
@ConditionalOnClass(DataSource.class)
@ConditionalOnProperty(prefix = "jiangyuan-transaction", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TransactionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TransactionAspect transactionAspect(DataSource dataSource) {
        return new TransactionAspect(dataSource);
    }
}