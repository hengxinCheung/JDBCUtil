package com.DAO;

import java.sql.SQLException;
import java.util.List;
import com.Bean.User;
import JDBCUtil.DataBaseUtil;

public class UserDAO extends DataBaseUtil{

	public UserDAO(){
		
	}
	
	public List<User> queryUserByUsername(String username) throws SQLException{
		String sql = "select * from user where username=?";
		
		return super.query(User.class, sql, username);
	}
}
