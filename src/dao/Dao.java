package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 存取管理
 * @author Shawn
 *
 */
public class Dao {
	private Conn cm = null;

	public Dao() {
		try {
			cm = new Conn();
		} catch (SQLException re) {
			re.printStackTrace();
		}
	}

	private Connection getConnect() throws SQLException {
		return cm.getConnection();
	}

	public void releaseConnection(Connection cn) throws SQLException {
		cm.releaseConnection(cn);
	}

	/**
	 * List查詢(不帶WHERE)
	 */
	public List sqlGet(String sql) throws SQLException {
		List resultList = new ArrayList();
		ResultSet resultSet = null;
		PreparedStatement ps = null;
		Connection cn = null;
		try {
			cn = getConnect();
			ps = cn.prepareStatement(sql);
			resultSet = ps.executeQuery();
			Map map;
			for (; resultSet.next(); resultList.add(map)) {
				map = doCreateRow(resultSet);
			}
		} catch (SQLException sqle) {
			throw sqle;
		} catch (NullPointerException e) {
		} finally {
			destroyConnection(resultSet, ps, cn);
		}
		return resultList;
	}	
	
	/**
	 * Map查詢
	 */
	public Map sqlGetMap(String sql)throws SQLException{
		List resultList = new ArrayList();
		ResultSet resultSet = null;
		PreparedStatement ps = null;
		Connection cn = null;
		Map map = null;
		try {
			cn = getConnect();
			ps = cn.prepareStatement(sql);
			resultSet = ps.executeQuery();			
			for (; resultSet.next(); resultList.add(map)) {
				map = doCreateRow(resultSet);
			}
		} catch (SQLException sqle) {
			throw sqle;
		} catch (NullPointerException e) {
		} finally {
			destroyConnection(resultSet, ps, cn);
		}
		return map;
	}
	
	/**
	 * 結束一次查詢連線
	 */
	private void destroyConnection(ResultSet resultSet, PreparedStatement ps, Connection cn){		
		try {
			resultSet.close();
			ps.close();
			releaseConnection(cn);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 結束一次動作連線
	 */
	private void destroyConnection(PreparedStatement ps, Connection cn){		
		try {
			ps.close();
			releaseConnection(cn);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void exSql(String sql) throws SQLException {
		Connection cn=null;
		PreparedStatement ps=null;
		try {
			cn = getConnect();
			ps = cn.prepareStatement(sql);
			ps.executeUpdate();
			ps.clearParameters(); 
		} catch (SQLException sqle) {
			//sqle.printStackTrace();
			throw sqle;
		} catch (NullPointerException e) {
			//e.printStackTrace();
		} finally {
			destroyConnection(ps, cn);
		}
	}

	
	

	/**
	 * 將執行sql結果放在Map中
	 */
	private final Map doCreateRow(ResultSet resultSet) throws SQLException {
		Map result = new HashMap();
		try {
			ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
			int count = resultSetMetaData.getColumnCount();
			for (int i = 1; i <= count; i++) {
				String label = resultSetMetaData.getColumnLabel(i);
				Object value = resultSet.getObject(i);
				// result.put(label.toUpperCase(), value);
				result.put(label, value);
			}
		} catch (SQLException e) {
			throw e;
		}
		return result;
	}
}