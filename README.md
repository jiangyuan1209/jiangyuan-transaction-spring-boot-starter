# JiangYuan Transaction Spring Boot Starter

基于 AOP + ThreadLocal 实现的轻量级声明式事务管理组件，提供类似 Spring `@Transactional` 的能力，但作为独立 Starter，适合理解事务传播机制原理。

## Maven 依赖

```xml
<dependency>
    <groupId>io.github.jiangyuan1209</groupId>
    <artifactId>jiangyuan-transaction-spring-boot-starter</artifactId>
    <version>0.0.1</version>
</dependency>
```

添加依赖即可使用，无需额外配置。Spring Boot 会自动加载事务管理功能。

## 特性

- 声明式 `@JTransaction` 注解，标注在方法或类上
- 支持 **7 种事务传播行为**：REQUIRED、SUPPORTS、MANDATORY、REQUIRES_NEW、NOT_SUPPORTED、NEVER、NESTED
- 支持事务隔离级别、超时时间、只读事务配置
- 支持自定义回滚规则（rollbackFor / noRollbackFor）
- NESTED 传播基于 JDBC Savepoint 实现
- REQUIRES_NEW / NOT_SUPPORTED 支持事务挂起与恢复
- 线程安全：基于 ThreadLocal 管理事务上下文

## 快速开始

### 1. 在方法上加注解

```java
import io.github.jiangyuan.transaction.annotation.JTransaction;
import io.github.jiangyuan.transaction.support.Propagation;

@JTransaction(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
public void createOrder(Order order) {
    // 所有数据库操作在同一事务中
    orderMapper.insert(order);
    inventoryMapper.reduce(order.getProductId(), order.getQuantity());
}
```

### 2. 在类上加注解（类中所有方法生效）

```java
@JTransaction(propagation = Propagation.REQUIRED)
@Service
public class OrderService {
    // 所有方法都在事务中执行
    public void methodA() { ... }
    public void methodB() { ... }
}
```

### 3. 配置文件

```yaml
jiangyuan-transaction:
  enabled: true              # 是否启用事务管理
  default-timeout: -1        # 默认超时时间（秒），-1 表示不超时
  default-read-only: false   # 默认是否为只读事务
```

## 参数说明

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| propagation | Propagation | `REQUIRED` | 事务传播行为 |
| isolation | int | `-1` | 事务隔离级别（使用数据库默认） |
| timeout | int | `-1` | 事务超时时间（秒） |
| readOnly | boolean | `false` | 是否为只读事务 |
| rollbackFor | Class[] | `{}` | 遇到哪些异常时回滚（空表示回滚 RuntimeException） |
| noRollbackFor | Class[] | `{}` | 遇到哪些异常时不回滚 |

## 事务传播行为详解

### REQUIRED（最常用）

有事务则加入，没有则新建。

```java
// A 开启了事务
@JTransaction(propagation = Propagation.REQUIRED)
public void methodA() {
    // B 加入 A 的事务
    methodB();
}

@JTransaction(propagation = Propagation.REQUIRED)
public void methodB() {
    // A 和 B 在同一事务中，一起提交或一起回滚
}
```

### REQUIRES_NEW

始终新建事务，挂起现有事务。

```java
@JTransaction(propagation = Propagation.REQUIRED)
public void methodA() {
    // B 会开启一个全新事务，A 的事务被挂起
    methodB();
    // A 继续执行，不受 B 影响
}

@JTransaction(propagation = Propagation.REQUIRES_NEW)
public void methodB() {
    // 独立事务，无论 A 是否回滚，B 都会独立提交
}
```

### NESTED

在当前事务内创建保存点，失败只回滚到保存点，不影响外部事务。

```java
@JTransaction(propagation = Propagation.REQUIRED)
public void methodA() {
    // B 在 A 的事务中，但以保存点隔离
    try {
        methodB();
    } catch (Exception e) {
        // B 失败了，回滚到保存点，A 可以继续
    }
}

@JTransaction(propagation = Propagation.NESTED)
public void methodB() {
    // 如果 B 抛出异常，回滚到保存点
}
```

### SUPPORTS

有事务则加入，没有则以非事务方式执行。

### NOT_SUPPORTED

以非事务方式执行，挂起现有事务。

### MANDATORY

必须在现有事务内，否则抛出异常。

### NEVER

必须在非事务下执行，有事务则抛出异常。

## 使用场景

### 场景 1：普通增删改（REQUIRED）

```java
@JTransaction(rollbackFor = Exception.class)
public void createUser(User user) {
    userMapper.insert(user);
    roleMapper.assign(user.getId(), "USER");
}
```

### 场景 2：记录操作日志不受主事务影响（REQUIRES_NEW）

```java
@JTransaction(rollbackFor = Exception.class)
public void placeOrder(Order order) {
    orderMapper.insert(order);
    // 记录日志独立提交，即使订单回滚日志也会保留
    logService.saveLog("下单", order.getId());
}

@JTransaction(propagation = Propagation.REQUIRES_NEW)
public void saveLog(String action, Long orderId) {
    logMapper.insert(action, orderId);
}
```

### 场景 3：部分失败不影响整体（NESTED）

```java
@JTransaction(rollbackFor = Exception.class)
public void batchUpdate(List<User> users) {
    for (User user : users) {
        try {
            updateUser(user);
        } catch (Exception e) {
            // 单个失败不影响其他用户
        }
    }
}

@JTransaction(propagation = Propagation.NESTED)
public void updateUser(User user) {
    userMapper.updateById(user);
}
```

## 注意事项

- **AOP 自调用问题**：同一 Bean 内部 `this.method()` 调用 `@JTransaction` 方法时 AOP 不生效（Spring AOP 代理限制），需通过代理对象调用或拆分到不同 Bean
- **仅支持 DataSource 事务**：当前实现基于 JDBC Connection，不支持 Redis、MQ 等外部资源的事务
- **NESTED 传播需要数据库支持**：MySQL 支持 Savepoint，部分数据库可能不支持
