package dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Vector;
import com.mysql.jdbc.Driver;

/**
 * 資料庫連接池
 * 
 * @author Shawn
 * 
 */
public class Conn {
	/** 連接數 */
	static int iRequestCount = 0;

	/** 連接Pool */
	static Vector connectionPool = null;

	/** 初始連接數 */
	static final int INIT_NUM_CONNECTION = 2;

	/** 追加連接數 */
	static final int ADD_NUM_CONNECTION = 1;

	/** 最大連接數 */
	static final int MAX_NUM_CONNECTION = 10;

	/** 最小連接數 */
	static final int MIN_NUM_CONNECTION = INIT_NUM_CONNECTION;

	/** 初始化標誌 */
	boolean bInitialized = false;

	static String serverName = "127.0.0.1";

	static String sDBDriver = "com.mysql.jdbc.Driver";

	static String dbInstance = "a2d";

	static String sConnStr = "jdbc:mysql://" + serverName + "/" + dbInstance;

	static String dbUser = "root";

	static String userPwd = "spring";
	static {
		try {
			Class.forName(sDBDriver);
			DriverManager.registerDriver(new Driver());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * 資料庫連接數
	 */
	class ConnectionPoolElement {
		Connection con;

		boolean used;
	}

	public Conn() throws SQLException {
		if (connectionPool == null) {
			connectionPool = new Vector();
		}
		init();
	}

	/**
	 * Connection的取得
	 */
	public synchronized Connection getConnection() throws SQLException {
		ConnectionPoolElement elm = null;
		for (;;) {
			synchronized (connectionPool) {
				for (int i = 0; i < connectionPool.size(); i++) {
					elm = (ConnectionPoolElement) (connectionPool.elementAt(i));
					if (!elm.used) {
						elm.used = true;
						return elm.con;
					}
				}
			}
			// 超過最大連接數，則追加
			if (connectionPool.size() < MAX_NUM_CONNECTION) {
				createConnectionPool(ADD_NUM_CONNECTION);
			} else {
				try {
					this.wait(100);
				} catch (InterruptedException e) {
				}
			}
		}
	}

	/**
	 * 連接完之後開始運用
	 */
	public synchronized void releaseConnection(Connection con)
			throws SQLException {
		ConnectionPoolElement elm;
		synchronized (connectionPool) {
			for (int i = 0; i < connectionPool.size(); i++) {
				elm = (ConnectionPoolElement) (connectionPool.elementAt(i));
				if (elm.con == con) {
					elm.used = false;
					return;
				}
			}
		}
		throw new SQLException("unknown Connection");
	}

	/**
	 * 資料庫初始化
	 */
	public void init() throws SQLException {
		if (bInitialized)
			return;
		synchronized (connectionPool) {
			if (connectionPool.size() < INIT_NUM_CONNECTION) {
				try {
					// 連結資料庫
					createConnectionPool(INIT_NUM_CONNECTION);
				} catch (Exception ex) {
					ex.printStackTrace();
					throw new SQLException("連線失敗");
				}
				synchronized (this) {
					iRequestCount++;
				}
			} else {
				synchronized (this) {
					iRequestCount++;
				}
			}
		}
		bInitialized = true;
	}

	/**
	 * 關閉資料庫連線
	 */
	public void destroy() {
		synchronized (this) {
			iRequestCount--;
		}
		if (iRequestCount < 0) {
			try {
				destroyConnection();
			} catch (SQLException ex) {
			}
		}
	}

	/**
	 * 設定ConnectionPool
	 */
	private synchronized void createConnectionPool(int numConnection)
			throws SQLException {
		ConnectionPoolElement elm;
		synchronized (connectionPool) {
			for (int i = 0; i < numConnection; i++) {
				elm = new ConnectionPoolElement();
				elm.con = DriverManager
						.getConnection(sConnStr, dbUser, userPwd);
				connectionPool.addElement(elm);
			}
		}
	}

	/**
	 * ConnectionPool的Connection的關閉
	 */
	synchronized void destroyConnection() throws SQLException {
		ConnectionPoolElement elm;
		synchronized (connectionPool) {
			for (int i = 0; i < connectionPool.size(); i++) {
				elm = (ConnectionPoolElement) (connectionPool.elementAt(i));
				elm.con.close();
			}
		}
	}

}