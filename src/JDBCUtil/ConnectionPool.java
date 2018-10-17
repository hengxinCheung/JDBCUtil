package JDBCUtil;

import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.Properties;


public class ConnectionPool {
	
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
								/* 调用close()方法，则将其回收到连接池中 */
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
	
	
	public void checkConnection(){

		System.out.println("Checking the valid of connection!");
		/* 检测connection是否有效 */
		for(int i=0;i<pool.size();++i){
			/* 只要有一个connection过期就移除全部 */
			try {
				if(!pool.get(i).isValid(10)){//10ms内响应
					System.out.println("Unvalid,Clear the Connection!");
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
}
