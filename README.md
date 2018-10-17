# JDBCUtil
在这个轻量级的工具类当中，使用了数据库连接池去提高数据库连接的高效性，并且使用了PreparedStatement来执行对SQL的预编译，能够有效防止SQL注入问题。
## 配置文件
在src文件下创建dbconfig.properties文件，并填写以下信息。

```
driver = com.mysql.jdbc.Driver
url = jdbc:mysql://127.0.0.1:3306/test?characterEncoding=utf8
username = root
password = root
jdbcConnectionInitSize =10
```
**注：** properties是一种通用简单的配置文件格式，以键值对作为其配置语法，继承于HashMap，故Java对其有十分方便和高效的读取以及管理方法。

## 数据库连接池
平常连接数据库的时候，首先需要获取到数据库的连接 Java中对应的是 Connection对象，建立获取数据库连接是比较消耗资源的，而且每次建立获取连接也比较浪费时间，可以试想，如果每次请求过来，需要访问数据库时，都去重新建立并获取新的连接，就会浪费大量的资源和时间，此时客户端的相应时间肯定会较长，这并不是我们想看到的。因此这时候我们就要想办法避免这种现象，所以这时候就可以用连接池来解决。

其实简单的说，连接池实现的主要目的就是，获取连接的时候从池中获取已经初始化好的连接，这样做就不用每次都建立新的连接，就能节省资源的消耗，也能提高效率。然后在用完连接以后，调用conn.close( )时， **利用动态代理将连接连接放回到连接池中，而不是将其关闭。** 那么下次请求过来了，可以继续使用这个连接。

那么，首先要解决的问题是，我们需要确保 **该连接池类只有一个实例，并提供该实例的全局访问点** 。这一点，在本文中通过 **单例设计模式(Singleton)** 来解决。详细的设计思想可以见<a href="https://github.com/CyC2018/CS-Notes/blob/master/notes/设计模式.md">设计模式</a>

下面我们逐步来编写数据库连接池的具体实现类。

- 首先我们创建一个ConnectionPool类，并严格遵守单例设计模式

``` java
package JDBCUtil;

public class ConnectionPool {
	
  	/* 
	 * 当ConnectionPool类加载时，静态内部类Holder没有被加载进内存。
	 * 只有当调用getInstance()方法触发时，Holder类才会被加载。
	 * 此时初始化INSTANCE实例，并且JVM能确保INSTANCE只被实例化一次。
	 * 具有优点：①延迟初始化②由JVM提供对线程安全的支持 
	 */
	private ConnectionPool(){}
	
	private static class Holder{
		private static final ConnectionPool INSTANCE = new ConnectionPool();
	}
	
	public static ConnectionPool getInstance(){
		return Holder.INSTANCE;
	}

```

- 然后我们利用静态代码块把配置文件加载进来，并初始化initSize个连接对象放入连接池中待用

``` java
  	/* 声明配置变量 */
	private static LinkedList<Connection> pool = new LinkedList<Connection>();
	private static String driver;
	private static String url;
	private static String username;
	private static String password;
	private static int jdbcConnectionInitSize;
	private static int max = 1;	//连接池连接对象数量=max*jdbcConnectionInitSize
	
	static{
		/* 利用反射机制读取配置文件  */
		InputStream is = ConnectionPool.class.getResourceAsStream("/dbconfig.properties");
		Properties prop = new Properties();
		try{
			/* 加载文件流  */
			prop.load(is);
			/* 读取配置项  */
			driver = prop.getProperty("driver");
			url = prop.getProperty("url");
			username = prop.getProperty("username");
			password = prop.getProperty("password");
			jdbcConnectionInitSize = Integer.parseInt(prop.getProperty("jdbcConnectionInitSize"));
			
			Class.forName(driver);
			
			/* 添加initsize个connection对象到连接池中 */
			addConnectionToPool();
			
		}catch(Exception e){
			e.printStackTrace();
		}
	}
  
  	/* 添加initsize个connection对象到连接池中 */
	private static void addConnectionToPool(){
		for(int i=0;i<jdbcConnectionInitSize;++i){
			try {
				Connection conn = DriverManager.getConnection(url,username,password);
				System.out.println("Create Connection "+conn);
				/* 将连接对象加入到连接池中  */
				pool.add(conn);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
    
	}
```

- 提供返回连接对象的公有方法：为连接对象添加动态代理以实现回收

``` java
 	/* 从线程池中得到连接对象 */
	public Connection getConnection(){
		
		/* 如果当前连接池中没有连接对象，并且没有达到最大连接对象数量 */
		if(pool.size()==0&&max<5){
			/* 添加initsize个connection对象到连接池中 */
			addConnectionToPool();
			++max;
		}
		
		/* 如果连接池中仍有连接对象 */
		if(pool.size()>0){
			/* 得到连接池中第一个连接对象 */
			final Connection connection = pool.removeFirst();
			System.out.println("Using connection "+connection);
			
			/* 返回连接对象，并为其添加动态代理以在对象关闭时回收到线程池 */
			return (Connection)Proxy.newProxyInstance(this.getClass().getClassLoader(), 
					connection.getClass().getInterfaces(), new InvocationHandler(){

						@Override
						public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
							if(!method.getName().equalsIgnoreCase("close")){
								/* 如果调用方法名非close，允许执行 */
								return method.invoke(connection, args);
							}
							else{
								/* 若调用close()方法，则将其回收到连接池中 */
								pool.addLast(connection);
								System.out.println("Recycling connection "+connection);
								
								return null;
							}
						}
				
				
			});
		}else{
			System.out.println("DataBase busying!");
		}
		
		return null;
	}
```

- 提供检查并更新连接对象的公有方法：连接对象具有时限性（一般为8个小时），需要定期检查并更新（设置定时器）

``` java
  /* 检查连接对象的是否过期等有效性 */
  public void checkConnection(){
	System.out.println("Checking the valid of connection!");
	/* 检测connection是否有效 */
	for(int i=0;i<pool.size();++i){
		/* 只要有一个connection过期就移除全部 */
		try {
			if(!pool.get(i).isValid(10)){//10ms内响应
				pool.clear(); /* 清除全部元素 */
				max = 1;
				/* 加入连接对象 */
				addConnectionToPool();
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
  }
  
```

## 数据库工具类DataBaseUtil
在这个类中我们将具体实现增删查改、释放资源、分页等功能。可以实现任何复杂的SQL,而且通过数据绑定的方式不会有SQL注入问题。

- 从连接池中获得数据库连接对象

``` java
package JDBCUtil;

public class DataBaseUtil {
	
	/* 得到连接池实例 */
	private static ConnectionPool pool = ConnectionPool.getInstance();

	/* 从连接池中获得数据库连接对象  */
	public static Connection getConnection(){
		return pool.getConnection();
	}
	
}
```

- 释放资源
``` java
/*
 * 释放资源
 * 包括连接对象、负责执行sql命令的statement对象、存储查询结果的ResultSet对象
 */
public static void release(Connection conn,Statement st,ResultSet rs){
	if(rs!=null){
		try {
			//关闭存储结果对象
			rs.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		rs = null;
	}

	if(st!=null){
		try {
			st.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	if(conn!=null){
		try {
			conn.close();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
```

- 查询记录：通过反射机制查询并注入到bean中
``` java
/*
 * 通过反射机制查询多条记录并将字段自动注入bean中
 * @param sql，sql语句
 * @param params，sql语句待绑定的参数数组
 * @param cls，bean的Class属性
 * @return list<T>，返回该bean的list集合
 * @throws Exception
 */
public <T> List<T> query(Class<T> cls,String sql,Object...args) throws SQLException{

	List<T> list = new ArrayList<T>();
	Connection connection = null;
	PreparedStatement pstm = null;
	ResultSet rs = null;

	try{
		connection = getConnection();
		pstm = connection.prepareStatement(sql);

		/* 设置参数 */
		for(int i=0;i<args.length;++i){
			pstm.setObject(i+1, args[i]);
		}

		rs = pstm.executeQuery();
		ResultSetMetaData rsmd = rs.getMetaData();
		int col_count = rsmd.getColumnCount();

		while(rs.next()){
			//通过反射机制创建一个实例
			T resultObject =  cls.newInstance();
			for(int i=0;i<col_count;++i){
				//获取列字段名
				String col_name = rsmd.getColumnName(i+1);
				//根据列名获取值
				Object col_value = rs.getObject(col_name);
				if(col_value == null)
					col_value = "";
				//通过Class.getDeclaredField(String name)获取类已声明的指定字段
				Field field = cls.getDeclaredField(col_name);
				//修改该字段的的的访问性，这样就可以访问private修饰的字段
				field.setAccessible(true);
				//设置该字段的值
				field.set(resultObject, col_value);
			}
			list.add(resultObject);
		}

	}catch(Exception e){
		e.printStackTrace();
	}finally{
		/* 释放资源 */
		release(connection,null,rs);
	}

	return list;
}
```

- 更新记录（增加、修改、删除）
``` java
/*
 * 更新记录(增加、删除、修改)
 * @param sql,sql语句
 * @param args,参数数组
 * @return 影响的行数
 */
public int update(String sql,Object...args){
	Connection connection = null;
	int row = 0;
	try{
		connection = getConnection();
		PreparedStatement pstm = connection.prepareStatement(sql);
		//填充sql占位符
		for(int i=0;i<args.length;++i){
			pstm.setObject(i+1, args[i]);
		}
		row = pstm.executeUpdate();
	}catch(Exception e){
		e.printStackTrace();
	}finally{
		release(connection,null,null);
	}

	return row;
}
```

- 分页操作：1.根据页面大小，得到页数；2.从第i条记录开始，往后得到n条记录
``` java
/* 得到页数
 * @param sql String,sql语句
 * @param pageSize int,页面大小即一页几条
 * @return pageCount int,页数
 */
public int getPageCount(String sql,int pageSize) throws SQLException{
	int pageCount = 0;
	Connection conn = null;
	Statement st = null;
	ResultSet rs = null;
	try{
		conn = getConnection();
		st = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
		rs = st.executeQuery(sql);
		rs.last();	//跳转到最后一条记录
		int size = rs.getRow();	//得到总记录条数
		System.out.println("RecordSize:"+size);
		pageCount = (size%pageSize==0)?(size/pageSize):(size/pageSize+1);

	}catch(Exception e){
		e.printStackTrace();
	}finally{
		release(conn,st,rs);
	}

	return pageCount;
}

/*
 * 查询从第i条记录开始往后的n条数据
 *@param sql String,sql语句
 *@param begin,开始位置
 *@param count，查询条数
 */
public <T> List<T> queryPage(Class<T> cls,String sql,int begin,int count,Object...args) throws SQLException{
	/* 查询指定条数的数据 */
	List<T> list = new ArrayList<T>();
	Connection connection = null;
	PreparedStatement pstm = null;
	ResultSet rs = null;

	try{
		connection = getConnection();
		pstm = connection.prepareStatement(sql);

		/* 设置参数 */
		for(int i=0;i<args.length;++i){
			pstm.setObject(i+1, args[i]);
		}

		rs = pstm.executeQuery();
		ResultSetMetaData rsmd = rs.getMetaData();

		int col_count = rsmd.getColumnCount();
		rs.absolute(begin-1);      //把结果集指针调整到当前应该显示的记录的开始的前一条

		while(rs.next()&&count>0){
			//通过反射机制创建一个实例
			T resultObject =  cls.newInstance();
			for(int i=0;i<col_count;++i){
				String col_name = rsmd.getColumnName(i+1);
				Object col_value = rs.getObject(col_name);
				if(col_value == null)
					col_value = "";
				Field field = cls.getDeclaredField(col_name);
				field.setAccessible(true);
				field.set(resultObject, col_value);
			}
			list.add(resultObject);
			--count;
		}

	}catch(Exception e){
		e.printStackTrace();
	}finally{
		release(connection,pstm,rs);
	}

	return list;
}
```

## 用例测试

- 创建一个user的bean类

``` java
package com.Bean;

public class User {
	private String username;
	private String password;
	
	/* 必须提供一个实例化的方法 */
	public User(){}

	/* 各字段的getter和setter方法 
	 * 点击MyEclipse-Source-Generate可自动生成
	 */
	........
}
```

- 创建一个User的数据访问对象类，实体DAO类需继承刚才编写的DataBaseUtil

``` java
package com.DAO;

public class UserDAO extends DataBaseUtil{
	/* 根据username查询user */
	public List<User> queryUserByUsername(String username) throws SQLException{
		String sql = "select * from user where username=?";
		
		return super.query(User.class, sql, username);
	}
	
	...其他具体方法....
}
```

- 测试结果
``` java
package test;

public class main {

	public static void main(String[] args) {
		try {
			UserDAO dao = new UserDAO();
			List<User> users = dao.queryUserByUsername("admin");
			for(User user:users){
				System.out.println(user.toString());
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	/* 运行结果
	 * Create Connection com.mysql.jdbc.JDBC4Connection@6267c3bb
	 * Create Connection com.mysql.jdbc.JDBC4Connection@7a07c5b4
	 * Create Connection com.mysql.jdbc.JDBC4Connection@41cf53f9
	 * Create Connection com.mysql.jdbc.JDBC4Connection@306a30c7
	 * Create Connection com.mysql.jdbc.JDBC4Connection@27fa135a
	 * Create Connection com.mysql.jdbc.JDBC4Connection@2b71fc7e
	 * Create Connection com.mysql.jdbc.JDBC4Connection@1a86f2f1
	 * Create Connection com.mysql.jdbc.JDBC4Connection@4b85612c
	 * Create Connection com.mysql.jdbc.JDBC4Connection@7aec35a
	 * Create Connection com.mysql.jdbc.JDBC4Connection@531d72ca
	 * Using connection com.mysql.jdbc.JDBC4Connection@6267c3bb
	 * Recycling connection com.mysql.jdbc.JDBC4Connection@6267c3bb
	 * User[username:admin;password:admin]
	 */
}

```
