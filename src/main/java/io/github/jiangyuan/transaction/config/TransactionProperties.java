package io.github.jiangyuan.transaction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 事务全局配置属性
 * <p>
 * 配置文件示例：
 * <pre>
 * jiangyuan-transaction:
 *   enabled: true
 *   default-timeout: 30
 *   default-read-only: false
 * </pre>
 * </p>
 */
@ConfigurationProperties(prefix = "jiangyuan-transaction")
public class TransactionProperties {

    /** 是否启用事务管理 */
    private boolean enabled = true;

    /** 默认事务超时时间（秒），-1 表示不超时 */
    private int defaultTimeout = -1;

    /** 默认是否为只读事务 */
    private boolean defaultReadOnly = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getDefaultTimeout() {
        return defaultTimeout;
    }

    public void setDefaultTimeout(int defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }

    public boolean isDefaultReadOnly() {
        return defaultReadOnly;
    }

    public void setDefaultReadOnly(boolean defaultReadOnly) {
        this.defaultReadOnly = defaultReadOnly;
    }
}