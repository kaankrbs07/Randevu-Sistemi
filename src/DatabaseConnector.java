import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

// Veritabanı Bağlantısı
public class DatabaseConnector {
	public static final String url = "jdbc:sqlserver://DESKTOP :1433;databaseName=Randevu sistemi;integrated Security=true;encrypt=false;";
	public static final String username = "";
	public static final String password = "";

	public static Connection connect() throws SQLException {

		return DriverManager.getConnection(url, username, password);
	}

}
