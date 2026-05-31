package io.github.jiangyuan.transaction.annotation;

import io.github.jiangyuan.transaction.support.Propagation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式事务注解，类似于 Spring 的 {@code @Transactional}。
 * <p>
 * 标注在方法或类上，当方法执行时自动管理数据库事务（开启、提交、回滚）。
 * 支持事务传播行为、隔离级别、超时、只读、回滚规则等配置。
 * </p>
 *
 * <pre>
 * &#64;JTransaction(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
 * public void createOrder(Order order) {
 *     // 方法内所有数据库操作在同一事务中
 * }
 * </pre>
 *
 * @see Propagation
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface JTransaction {

    /**
     * 事务传播行为，默认 REQUIRED
     * <ul>
     *   <li>REQUIRED: 加入现有事务，没有则新建（最常用）</li>
     *   <li>REQUIRES_NEW: 始终新建事务，挂起现有事务</li>
     *   <li>NESTED: 在现有事务内创建保存点子事务</li>
     *   <li>SUPPORTS: 有事务则加入，无则以非事务方式执行</li>
     *   <li>NOT_SUPPORTED: 以非事务方式执行，挂起现有事务</li>
     *   <li>MANDATORY: 必须在现有事务内，否则抛异常</li>
     *   <li>NEVER: 必须在非事务下执行，有事务则抛异常</li>
     * </ul>
     */
    Propagation propagation() default Propagation.REQUIRED;

    /**
     * 事务隔离级别，默认使用数据库默认
     * <ul>
     *   <li>DEFAULT: 使用数据库默认</li>
     *   <li>READ_UNCOMMITTED: 读未提交</li>
     *   <li>READ_COMMITTED: 读已提交</li>
     *   <li>REPEATABLE_READ: 可重复读</li>
     *   <li>SERIALIZABLE: 串行化</li>
     * </ul>
     */
    int isolation() default -1;

    /**
     * 事务超时时间（秒），默认 -1 表示不超时
     */
    int timeout() default -1;

    /**
     * 是否为只读事务，默认 false
     * <p>用于只读查询场景，数据库可做优化</p>
     */
    boolean readOnly() default false;

    /**
     * 遇到哪些异常时回滚，默认所有 Exception 及其子类都回滚
     * <p>空数组表示回滚所有 RuntimeException 及其子类</p>
     */
    Class<? extends Throwable>[] rollbackFor() default {};

    /**
     * 遇到哪些异常时不回滚
     */
    Class<? extends Throwable>[] noRollbackFor() default {};
}
