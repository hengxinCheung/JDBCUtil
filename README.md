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
