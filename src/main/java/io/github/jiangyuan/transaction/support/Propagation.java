package io.github.jiangyuan.transaction.support;

/**
 * 事务传播行为枚举
 *
 * @see org.springframework.transaction.annotation.Propagation
 */
public enum Propagation {

    /**
     * 支持当前事务，如果当前没有事务，就新建一个事务。这是最常见的选择。
     */
    REQUIRED,

    /**
     * 支持当前事务，如果当前没有事务，就以非事务方式执行。
     */
    SUPPORTS,

    /**
     * 支持当前事务，如果当前没有事务，就抛出异常。
     */
    MANDATORY,

    /**
     * 新建事务，如果当前存在事务，把当前事务挂起。
     */
    REQUIRES_NEW,

    /**
     * 以非事务方式执行操作，如果当前存在事务，就把当前事务挂起。
     */
    NOT_SUPPORTED,

    /**
     * 以非事务方式执行，如果当前存在事务，则抛出异常。
     */
    NEVER,

    /**
     * 支持当前事务，如果当前事务存在，则嵌套在一个保存点中执行。
     * 如果嵌套事务失败，仅回滚到保存点，不影响外部事务。
     */
    NESTED
}