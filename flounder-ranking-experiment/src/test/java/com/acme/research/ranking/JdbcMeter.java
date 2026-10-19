package com.acme.research.ranking;
import javax.sql.DataSource;
import java.lang.reflect.*;
import java.sql.*;
import org.springframework.jdbc.datasource.DelegatingDataSource;
public final class JdbcMeter extends DelegatingDataSource {
 public static final ThreadLocal<Counts> ACTIVE=new ThreadLocal<>();
 public static final class Counts { public long executions,batchExecutions,batchItems,rowsConsumed; }
 public JdbcMeter(DataSource target) { super(target); }
 @Override public Connection getConnection() throws SQLException { return connection(super.getConnection()); }
 @Override public Connection getConnection(String user,String password) throws SQLException { return connection(super.getConnection(user,password)); }
 private static Object invoke(Object target,Method method,Object[] args) throws Throwable {
  try { return method.invoke(target,args); } catch (InvocationTargetException e) { throw e.getCause(); }
 }
 private static Connection connection(Connection raw) {
  Counts c=ACTIVE.get();
  if(c==null) return raw;
  return (Connection)Proxy.newProxyInstance(JdbcMeter.class.getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
   Object value=invoke(raw,m,a);
   if(value instanceof CallableStatement s) return statement(s,CallableStatement.class,c);
   if(value instanceof PreparedStatement s) return statement(s,PreparedStatement.class,c);
   if(value instanceof Statement s) return statement(s,Statement.class,c);
   return value;
  });
 }
 private static Object statement(Statement raw,Class<?> type,Counts c) {
  return Proxy.newProxyInstance(JdbcMeter.class.getClassLoader(),new Class<?>[]{type},(p,m,a)->{
   String n=m.getName();
   if(n.equals("addBatch")) c.batchItems++;
   if(n.equals("executeBatch")||n.equals("executeLargeBatch")) {c.executions++;c.batchExecutions++;}
   else if(n.equals("execute")||n.equals("executeQuery")||n.equals("executeUpdate")||n.equals("executeLargeUpdate")) c.executions++;
   Object value=invoke(raw,m,a);
   if(value instanceof ResultSet rs) return Proxy.newProxyInstance(JdbcMeter.class.getClassLoader(),new Class<?>[]{ResultSet.class},(rp,rm,ra)->{
    Object rv=invoke(rs,rm,ra);
    if(rm.getName().equals("next") && Boolean.TRUE.equals(rv)) c.rowsConsumed++;
    return rv;
   });
   return value;
  });
 }
}
