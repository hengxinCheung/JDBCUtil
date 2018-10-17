package JDBCUtil;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;



public class DataBaseUtil {

	private static ConnectionPool pool = ConnectionPool.getInstance();
	
	/* 从连接池中获得数据库连接对象  */
	public static Connection getConnection(){
		return pool.getConnection();
	}
	
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
					String col_name = rsmd.getColumnName(i+1);
					Object col_value = rs.getObject(col_name);
					if(col_value == null)
						col_value = "";
					
					Field field = cls.getDeclaredField(col_name);
					field.setAccessible(true);
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
	
	/*
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
	//查询指定条数的数据
	public <T> List<T> queryPage(Class<T> cls,String sql,int begin,int count,Object...args) throws SQLException{
		
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
	
}

