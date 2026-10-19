package com.acme.research.ranking;

import java.lang.reflect.*;
import java.sql.*;
import javax.sql.DataSource;

final class JdbcMetrics {
    static final class Sample {
        long executeCalls, batchItems, rowsRead;
    }
    private static final ThreadLocal<Sample> ACTIVE = new ThreadLocal<>();
    static Sample start() { Sample s = new Sample(); ACTIVE.set(s); return s; }
    static void stop() { ACTIVE.remove(); }
    static Object call(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException e) { throw e.getCause(); }
    }
    static DataSource wrap(DataSource target) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class}, (p,m,a) -> {
                    Object result = call(target,m,a);
                    return result instanceof Connection c ? connection(c) : result;
                });
    }
    private static Connection connection(Connection target) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (p,m,a) -> {
                    Object result = call(target,m,a);
                    return result instanceof Statement s ? statement(s) : result;
                });
    }
    private static Statement statement(Statement target) {
        Class<?> type = target instanceof CallableStatement ? CallableStatement.class
                : target instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
        long[] batch = {0};
        return (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
                new Class<?>[]{type}, (p,m,a) -> {
                    if (m.getName().equals("addBatch")) batch[0]++;
                    if (m.getName().equals("clearBatch")) batch[0]=0;
                    Sample sample = ACTIVE.get();
                    if (sample != null && m.getName().startsWith("execute")) {
                        sample.executeCalls++;
                        if (m.getName().contains("Batch")) sample.batchItems += batch[0];
                    }
                    Object result = call(target,m,a);
                    if (m.getName().contains("execute") && m.getName().contains("Batch")) batch[0]=0;
                    return result instanceof ResultSet r ? resultSet(r) : result;
                });
    }
    private static ResultSet resultSet(ResultSet target) {
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class}, (p,m,a) -> {
                    Object result = call(target,m,a);
                    Sample sample = ACTIVE.get();
                    if (sample != null && m.getName().equals("next") && Boolean.TRUE.equals(result))
                        sample.rowsRead++;
                    return result;
                });
    }
}
