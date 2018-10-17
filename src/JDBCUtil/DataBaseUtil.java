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
	
	/* �����ӳ��л�����ݿ����Ӷ���  */
	public static Connection getConnection(){
		return pool.getConnection();
	}
	
	/*
	 * �ͷ���Դ
	 * �������Ӷ��󡢸���ִ��sql�����statement���󡢴洢��ѯ�����ResultSet����
	 */
	public static void release(Connection conn,Statement st,ResultSet rs){
		if(rs!=null){
			try {
				//�رմ洢�������
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
	 * ͨ��������Ʋ�ѯ������¼�����ֶ��Զ�ע��bean��
	 * @param sql��sql���
	 * @param params��sql�����󶨵Ĳ�������
	 * @param cls��bean��Class����
	 * @return list<T>�����ظ�bean��list����
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
			
			/* ���ò��� */
			for(int i=0;i<args.length;++i){
				pstm.setObject(i+1, args[i]);
			}
			
			rs = pstm.executeQuery();
			ResultSetMetaData rsmd = rs.getMetaData();
			int col_count = rsmd.getColumnCount();
			
			while(rs.next()){
				//ͨ��������ƴ���һ��ʵ��
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
			/* �ͷ���Դ */
			release(connection,null,rs);
		}
		
		return list;
	}
	
	/*
	 * ���¼�¼(���ӡ�ɾ�����޸�)
	 * @param sql,sql���
	 * @param args,��������
	 * @return Ӱ�������
	 */
	public int update(String sql,Object...args){
		Connection connection = null;
		int row = 0;
		try{
			connection = getConnection();
			PreparedStatement pstm = connection.prepareStatement(sql);
			//���sqlռλ��
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
	 * @param sql String,sql���
	 * @param pageSize int,ҳ���С��һҳ����
	 * @return pageCount int,ҳ��
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
			rs.last();	//��ת�����һ����¼
			int size = rs.getRow();	//�õ��ܼ�¼����
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
	 * ��ѯ�ӵ�i����¼��ʼ�����n������
	 *@param sql String,sql���
	 *@param begin,��ʼλ��
	 *@param count����ѯ����
	 */
	//��ѯָ������������
	public <T> List<T> queryPage(Class<T> cls,String sql,int begin,int count,Object...args) throws SQLException{
		
		List<T> list = new ArrayList<T>();
		Connection connection = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		
		try{
			connection = getConnection();
			pstm = connection.prepareStatement(sql);
			
			/* ���ò��� */
			for(int i=0;i<args.length;++i){
				pstm.setObject(i+1, args[i]);
			}
			
			rs = pstm.executeQuery();
			ResultSetMetaData rsmd = rs.getMetaData();
			
			int col_count = rsmd.getColumnCount();
			rs.absolute(begin-1);      //�ѽ����ָ���������ǰӦ����ʾ�ļ�¼�Ŀ�ʼ��ǰһ��
			
			while(rs.next()&&count>0){
				//ͨ��������ƴ���һ��ʵ��
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

