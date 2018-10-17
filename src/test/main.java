package test;

import java.sql.SQLException;
import java.util.List;

import com.Bean.User;
import com.DAO.UserDAO;

public class main {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		UserDAO dao = new UserDAO();
		
		try {
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
