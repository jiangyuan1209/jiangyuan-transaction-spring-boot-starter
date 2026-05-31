package io.github.jiangyuan.transaction.support;

import java.sql.Connection;
import java.sql.Savepoint;

/**
 * 事务上下文持有者，使用 ThreadLocal 管理当前线程的事务状态。
 * <p>
 * 用于支持事务传播行为，记录当前线程的数据库连接、事务深度、保存点等信息。
 * </p>
 */
public final class TransactionContextHolder {

    private static final ThreadLocal<TransactionContext> CURRENT = new ThreadLocal<>();

    private TransactionContextHolder() {}

    /**
     * 获取当前线程的事务上下文，如果没有则创建一个新的。
     */
    public static TransactionContext current() {
        TransactionContext context = CURRENT.get();
        if (context == null) {
            context = new TransactionContext();
            CURRENT.set(context);
        }
        return context;
    }

    /**
     * 清除当前线程的事务上下文。
     */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * 单个线程的事务状态上下文。
     */
    public static class TransactionContext {

        /** 当前持有的数据库连接（物理连接） */
        private Connection connection;

        /** 事务嵌套深度，0 表示无事务 */
        private int depth = 0;

        /** 当前保存点（NESTED 传播时使用） */
        private Savepoint savepoint;

        /** 原始 autoCommit 状态 */
        private Boolean originalAutoCommit;

        /** 原始隔离级别 */
        private Integer originalIsolation;

        /** 是否需要全局回滚（子事务标记回滚时传递到外层） */
        private boolean rollbackOnly = false;

        public Connection getConnection() {
            return connection;
        }

        public void setConnection(Connection connection) {
            this.connection = connection;
        }

        public int getDepth() {
            return depth;
        }

        public void setDepth(int depth) {
            this.depth = depth;
        }

        public void incrementDepth() {
            this.depth++;
        }

        public void decrementDepth() {
            this.depth--;
        }

        public Savepoint getSavepoint() {
            return savepoint;
        }

        public void setSavepoint(Savepoint savepoint) {
            this.savepoint = savepoint;
        }

        public Boolean getOriginalAutoCommit() {
            return originalAutoCommit;
        }

        public void setOriginalAutoCommit(Boolean originalAutoCommit) {
            this.originalAutoCommit = originalAutoCommit;
        }

        public Integer getOriginalIsolation() {
            return originalIsolation;
        }

        public void setOriginalIsolation(Integer originalIsolation) {
            this.originalIsolation = originalIsolation;
        }

        public boolean isRollbackOnly() {
            return rollbackOnly;
        }

        public void setRollbackOnly(boolean rollbackOnly) {
            this.rollbackOnly = rollbackOnly;
        }

        /** 是否处于事务中 */
        public boolean inTransaction() {
            return depth > 0;
        }
    }
}
