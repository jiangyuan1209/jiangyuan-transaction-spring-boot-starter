package io.github.jiangyuan.transaction.aspect;

import io.github.jiangyuan.transaction.annotation.JTransaction;
import io.github.jiangyuan.transaction.support.Propagation;
import io.github.jiangyuan.transaction.support.TransactionContextHolder;
import io.github.jiangyuan.transaction.support.TransactionContextHolder.TransactionContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;

/**
 * 事务 AOP 切面
 * <p>
 * 拦截带有 {@link JTransaction} 注解的方法，根据配置的传播行为管理数据库事务。
 * 核心逻辑：
 * <ul>
 *   <li>REQUIRED: 已有事务则加入（depth++），无则新建（连接 autoCommit=false）</li>
 *   <li>REQUIRES_NEW: 挂起当前事务，开启全新事务</li>
 *   <li>NESTED: 在当前事务上创建保存点，失败只回滚到保存点</li>
 *   <li>SUPPORTS: 有事务则加入，无则非事务执行</li>
 *   <li>NOT_SUPPORTED: 挂起当前事务，非事务执行</li>
 *   <li>MANDATORY: 必须有事务，否则抛异常</li>
 *   <li>NEVER: 必须无事务，否则抛异常</li>
 * </ul>
 * </p>
 */
@Aspect
public class TransactionAspect {

    private static final Logger log = LoggerFactory.getLogger(TransactionAspect.class);

    private final DataSource dataSource;

    public TransactionAspect(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Around("@annotation(io.github.jiangyuan.transaction.annotation.JTransaction)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        JTransaction annotation = getAnnotation(joinPoint);
        if (annotation == null) {
            return joinPoint.proceed();
        }

        TransactionContext ctx = TransactionContextHolder.current();
        Propagation propagation = annotation.propagation();

        switch (propagation) {
            case REQUIRED:
                return handleRequired(joinPoint, ctx, annotation);
            case SUPPORTS:
                return handleSupports(joinPoint, ctx, annotation);
            case MANDATORY:
                return handleMandatory(joinPoint, ctx, annotation);
            case REQUIRES_NEW:
                return handleRequiresNew(joinPoint, ctx, annotation);
            case NOT_SUPPORTED:
                return handleNotSupported(joinPoint, ctx, annotation);
            case NEVER:
                return handleNever(joinPoint, ctx);
            case NESTED:
                return handleNested(joinPoint, ctx, annotation);
            default:
                return joinPoint.proceed();
        }
    }

    // ==================== Propagation handlers ====================

    private Object handleRequired(ProceedingJoinPoint jp, TransactionContext ctx, JTransaction ann) throws Throwable {
        if (ctx.inTransaction()) {
            // 加入现有事务
            ctx.incrementDepth();
            try {
                return jp.proceed();
            } catch (Throwable ex) {
                handleRollback(ctx, ex, ann);
                throw ex;
            } finally {
                ctx.decrementDepth();
            }
        } else {
            // 开启新事务
            return executeNewTransaction(jp, ctx, ann, false);
        }
    }

    private Object handleSupports(ProceedingJoinPoint jp, TransactionContext ctx, JTransaction ann) throws Throwable {
        if (ctx.inTransaction()) {
            ctx.incrementDepth();
            try {
                return jp.proceed();
            } catch (Throwable ex) {
                handleRollback(ctx, ex, ann);
                throw ex;
            } finally {
                ctx.decrementDepth();
            }
        } else {
            // 非事务方式执行
            return jp.proceed();
        }
    }

    private Object handleMandatory(ProceedingJoinPoint jp, TransactionContext ctx, JTransaction ann) throws Throwable {
        if (!ctx.inTransaction()) {
            throw new IllegalStateException(
                    "No existing transaction found for method marked with propagation=MANDATORY");
        }
        ctx.incrementDepth();
        try {
            return jp.proceed();
        } catch (Throwable ex) {
            handleRollback(ctx, ex, ann);
            throw ex;
        } finally {
            ctx.decrementDepth();
        }
    }

    private Object handleRequiresNew(ProceedingJoinPoint jp, TransactionContext ctx, JTransaction ann) throws Throwable {
        // 保存并挂起当前事务上下文
        TransactionContext suspended = suspendContext(ctx);
        try {
            TransactionContext newCtx = TransactionContextHolder.current();
            return executeNewTransaction(jp, newCtx, ann, false);
        } finally {
            // 恢复挂起的事务上下文
            resumeContext(ctx, suspended);
        }
    }

    private Object handleNotSupported(ProceedingJoinPoint jp, TransactionContext ctx, JTransaction ann) throws Throwable {
        // 挂起当前事务，非事务执行
        TransactionContext suspended = suspendContext(ctx);
        try {
            return jp.proceed();
        } finally {
            resumeContext(ctx, suspended);
        }
    }

    private Object handleNever(ProceedingJoinPoint jp, TransactionContext ctx) throws Throwable {
        if (ctx.inTransaction()) {
            throw new IllegalStateException(
                    "Existing transaction found for method marked with propagation=NEVER");
        }
        return jp.proceed();
    }

    private Object handleNested(ProceedingJoinPoint jp, TransactionContext ctx, JTransaction ann) throws Throwable {
        if (ctx.inTransaction()) {
            // 在现有事务中创建保存点
            Savepoint savepoint = null;
            try {
                savepoint = ctx.getConnection().setSavepoint();
                ctx.setSavepoint(savepoint);
                ctx.incrementDepth();

                Object result = jp.proceed();

                // 如果标记了 rollbackOnly，回滚到保存点
                if (ctx.isRollbackOnly()) {
                    ctx.getConnection().rollback(savepoint);
                    ctx.setRollbackOnly(false);
                }
                return result;
            } catch (Throwable ex) {
                if (ctx.isRollbackOnly()) {
                    ctx.getConnection().rollback(savepoint);
                    ctx.setRollbackOnly(false);
                }
                throw ex;
            } finally {
                ctx.setSavepoint(null);
                ctx.decrementDepth();
            }
        } else {
            // 没有现有事务，行为同 REQUIRED
            return executeNewTransaction(jp, ctx, ann, false);
        }
    }

    // ==================== Core transaction execution ====================

    /**
     * 在一个全新的事务中执行方法。
     */
    private Object executeNewTransaction(ProceedingJoinPoint jp, TransactionContext ctx,
                                         JTransaction ann, boolean isRequiresNew) throws Throwable {
        Connection conn = null;
        Boolean originalAutoCommit = null;
        Integer originalIsolation = null;

        try {
            conn = dataSource.getConnection();
            originalAutoCommit = conn.getAutoCommit();
            originalIsolation = conn.getTransactionIsolation();

            conn.setAutoCommit(false);

            // 设置隔离级别
            if (ann.isolation() != -1) {
                conn.setTransactionIsolation(ann.isolation());
            }

            // 设置只读
            if (ann.readOnly()) {
                conn.setReadOnly(true);
            }

            // 设置超时
            if (ann.timeout() > 0) {
                conn.setNetworkTimeout(null, ann.timeout() * 1000);
            }

            // 绑定到线程上下文
            ctx.setConnection(conn);
            ctx.setOriginalAutoCommit(originalAutoCommit);
            ctx.setOriginalIsolation(originalIsolation);
            ctx.setDepth(1);

            Object result = jp.proceed();

            // 提交事务
            if (!ctx.isRollbackOnly()) {
                conn.commit();
            } else {
                conn.rollback();
                ctx.setRollbackOnly(false);
            }

            return result;
        } catch (Throwable ex) {
            // 回滚事务
            rollbackTransaction(ctx, conn);
            throw ex;
        } finally {
            restoreConnection(conn, originalAutoCommit, originalIsolation);
            // 清理事务上下文
            if (!isRequiresNew) {
                TransactionContextHolder.clear();
            }
        }
    }

    // ==================== Rollback logic ====================

    private void handleRollback(TransactionContext ctx, Throwable ex, JTransaction ann) {
        if (shouldRollback(ex, ann)) {
            // 如果是 NESTED，回滚到保存点
            if (ctx.getSavepoint() != null) {
                try {
                    ctx.getConnection().rollback(ctx.getSavepoint());
                    ctx.setSavepoint(null);
                } catch (SQLException e) {
                    log.error("Failed to rollback to savepoint", e);
                }
            } else if (ctx.getDepth() <= 1) {
                // 最外层事务，标记 rollbackOnly
                ctx.setRollbackOnly(true);
            } else {
                // 内层 REQUIRED 传播，将回滚标记传递到外层
                ctx.setRollbackOnly(true);
            }
        }
    }

    private boolean shouldRollback(Throwable ex, JTransaction ann) {
        // 检查 noRollbackFor
        for (Class<? extends Throwable> noRollback : ann.noRollbackFor()) {
            if (noRollback.isInstance(ex)) {
                return false;
            }
        }
        // 检查 rollbackFor
        if (ann.rollbackFor().length > 0) {
            for (Class<? extends Throwable> rollbackType : ann.rollbackFor()) {
                if (rollbackType.isInstance(ex)) {
                    return true;
                }
            }
            return false;
        }
        // 默认：RuntimeException 和 Error 回滚
        return (ex instanceof RuntimeException || ex instanceof Error);
    }

    private void rollbackTransaction(TransactionContext ctx, Connection conn) {
        try {
            if (conn != null && !conn.isClosed()) {
                if (ctx != null && ctx.getSavepoint() != null) {
                    conn.rollback(ctx.getSavepoint());
                } else {
                    conn.rollback();
                }
            }
        } catch (SQLException e) {
            log.error("Failed to rollback transaction", e);
        }
    }

    private void restoreConnection(Connection conn, Boolean originalAutoCommit, Integer originalIsolation) {
        try {
            if (conn != null && !conn.isClosed()) {
                if (originalAutoCommit != null) {
                    conn.setAutoCommit(originalAutoCommit);
                }
                if (originalIsolation != null) {
                    conn.setTransactionIsolation(originalIsolation);
                }
                conn.setReadOnly(false);
                conn.close();
            }
        } catch (SQLException e) {
            log.warn("Failed to restore connection", e);
        }
    }

    // ==================== Context suspend/resume ====================

    /**
     * 挂起当前事务上下文，保存现场。
     */
    private TransactionContext suspendContext(TransactionContext ctx) {
        TransactionContext suspended = new TransactionContext();
        suspended.setConnection(ctx.getConnection());
        suspended.setDepth(ctx.getDepth());
        suspended.setOriginalAutoCommit(ctx.getOriginalAutoCommit());
        suspended.setOriginalIsolation(ctx.getOriginalIsolation());
        suspended.setSavepoint(ctx.getSavepoint());
        suspended.setRollbackOnly(ctx.isRollbackOnly());

        // 清空当前上下文
        TransactionContextHolder.clear();
        return suspended;
    }

    /**
     * 恢复被挂起的事务上下文。
     */
    private void resumeContext(TransactionContext ctx, TransactionContext suspended) {
        ctx.setConnection(suspended.getConnection());
        ctx.setDepth(suspended.getDepth());
        ctx.setOriginalAutoCommit(suspended.getOriginalAutoCommit());
        ctx.setOriginalIsolation(suspended.getOriginalIsolation());
        ctx.setSavepoint(suspended.getSavepoint());
        ctx.setRollbackOnly(suspended.isRollbackOnly());
    }

    // ==================== Utility ====================

    private JTransaction getAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 先查方法上的注解
        JTransaction ann = method.getAnnotation(JTransaction.class);
        if (ann != null) {
            return ann;
        }

        // 再查类上的注解
        return joinPoint.getTarget().getClass().getAnnotation(JTransaction.class);
    }
}