package krusty;

import spark.Request;
import spark.Response;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;

public class Database {

	private static final String jdbcString = "jdbc:mysql://puccini.cs.lth.se/hbg01";
	private static final String jdbcUsername = "hbg01";
	private static final String jdbcPassword = "ayn041ct";

	private Connection conn;

	public void connect() {
		try {
			Class.forName("com.mysql.jdbc.Driver");
			conn = DriverManager.getConnection(jdbcString, jdbcUsername, jdbcPassword);
			System.out.println("Database connection success!");
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public String getCustomers(Request req, Response res) {
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			stmt = conn.prepareStatement("SELECT CompanyName AS name, Address AS address"
					+
					" FROM Customers");
			rs = stmt.executeQuery();
			String toJSON = krusty.Jsonizer.toJson(rs, "customers");
			return toJSON;
		} catch (SQLException e) {
			System.out.println("getCustomers request failed");
			e.printStackTrace();
			return null;
		}
	}

	public String getRawMaterials(Request req, Response res) {
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			stmt = conn.prepareStatement(
					"SELECT IngredientType AS name, AmountInStorage AS amount, Unit AS unit" +
							" FROM Ingredients;");
			rs = stmt.executeQuery();
			String toJSON = krusty.Jsonizer.toJson(rs, "raw-materials");
			return toJSON;
		} catch (SQLException e) {
			System.out.println("getRawMaterials request failed");
			e.printStackTrace();
			return null;
		}
	}

	public String getCookies(Request req, Response res) {
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			stmt = conn.prepareStatement("SELECT Type AS name"
					+
					" FROM Cookies");
			rs = stmt.executeQuery();
			String toJSON = krusty.Jsonizer.toJson(rs, "cookies");
			return toJSON;
		} catch (SQLException e) {
			System.out.println("getCookies request failed");
			e.printStackTrace();
			return null;
		}
	}

	public String getRecipes(Request req, Response res) {
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			stmt = conn.prepareStatement(
					"SELECT CookieType AS cookie, IngredientType AS raw_material, Amount as amount, Unit as unit"
							+ " FROM RecipeAmounts, Ingredients WHERE RecipeAmounts.IngredientType = Ingredients.IngredientType");
			rs = stmt.executeQuery();
			String toJSON = krusty.Jsonizer.toJson(rs, "recipes");
			return toJSON;
		} catch (SQLException e) {
			System.out.println("getRecipes request failed");
			e.printStackTrace();
			return null;
		}
	}

	public String getPallets(Request req, Response res) {
		PreparedStatement stmt = null;
		ResultSet rs = null;
		String SQL = "SELECT PalletId AS id, CookieType AS cookie, ProdDateTime AS production_date, IFNULL(CompanyName, 'null') AS customer, IF(IsBlocked, 'yes', 'no') AS blocked"
				+ " FROM Pallets LEFT JOIN Orders ON Pallets.OrderId = Orders.OrderId LEFT JOIN Customers ON Orders.CustomerId = Customers.CustomerId";
		String cookie = req.queryParams("cookie");
		String from = req.queryParams("from");
		String to = req.queryParams("to");
		String blocked = req.queryParams("blocked");

		if (req.queryParams("blocked") != null) {
			blocked = (req.queryParams("blocked").equals("yes")) ? "TRUE" : "FALSE";
		}

		if (cookie != null || from != null || to != null || blocked != null) {
			SQL += " WHERE";

			if (cookie != null) {
				SQL += " CookieType = '" + cookie + "'";
			}
			if (from != null) {
				if (cookie != null) {
					SQL += " AND";
				}
				SQL += " DATE(ProdDateTime) >= '" + from + "'";
			}
			if (to != null) {
				if (cookie != null || from != null) {
					SQL += " AND";
				}
				SQL += " DATE(ProdDateTime) <= '" + to + "'";
			}
			if (blocked != null) {
				if (cookie != null || from != null || to != null) {
					SQL += " AND";
				}
				SQL += " IsBlocked = " + blocked;
			}
		}
		try {
			stmt = conn.prepareStatement(SQL);
			rs = stmt.executeQuery();
			String toJSON = Jsonizer.toJson(rs, "pallets");
			return toJSON;
		} catch (SQLException e) {
			System.out.println("getPallets request failed");
			e.printStackTrace();
			return null;
		}
	}

	public String reset(Request req, Response res) {
		Scanner scan = null;
		PreparedStatement stmt = null;

		String[] sqlStatements = {
				"SET FOREIGN_KEY_CHECKS=0",
				"TRUNCATE TABLE CookieQuantities",
				"TRUNCATE TABLE Orders",
				"TRUNCATE TABLE Customers",
				"TRUNCATE TABLE RecipeAmounts",
				"TRUNCATE TABLE Ingredients",
				"TRUNCATE TABLE Cookies",
				"TRUNCATE TABLE Pallets",
				"SET FOREIGN_KEY_CHECKS=1"
		};

		try {
			conn.setAutoCommit(false);
			for (String sql : sqlStatements) {
				stmt = conn.prepareStatement(sql);
				stmt.executeUpdate();
			}
			conn.commit();
		} catch (SQLException e) {
			try {
				conn.rollback();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}
			e.printStackTrace();
		} finally {
			try {
				conn.setAutoCommit(true);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		String sql;
		File resetFile = new File("src\\main\\resources\\public\\reset.txt");
		try {
			scan = new Scanner(new FileInputStream(resetFile), StandardCharsets.UTF_8);
		} catch (FileNotFoundException e) {
			System.out.println("File reset.txt can't be found");
			e.printStackTrace();
		}
		sql = "";
		while (scan.hasNextLine()) {
			String append = scan.nextLine();
			do {
				append = scan.nextLine();
				sql += append;
			} while (!append.isEmpty() && scan.hasNextLine());
			try {
				stmt = conn.prepareStatement(sql);
				stmt.executeUpdate();
			} catch (SQLException e) {
				System.out.print("Statement execution failed");
				e.printStackTrace();
			}
			sql = "";
		}
		return krusty.Jsonizer.anythingToJson("ok", "status");
	}

	public String createPallet(Request req, Response res) {
		PreparedStatement stmt = null;
		ResultSet rs = null;
		String cookie = req.queryParams("cookie");
		String sql = "SELECT Type FROM Cookies WHERE Type = '" + cookie + "';";

		try {
			stmt = conn.prepareStatement(sql);
			rs = stmt.executeQuery();
			if (!rs.next()) {
				return krusty.Jsonizer.anythingToJson("unknown cookie", "status");
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return krusty.Jsonizer.anythingToJson("error", "status");
		}

		sql = "INSERT INTO Pallets (CookieType, ProdDateTime, HasLeftFactory, IsBlocked) VALUES ('" + cookie
				+ "', NOW(), False, False);";
		String toJSON = "";

		try {

			conn.setAutoCommit(false);
			stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			stmt.executeUpdate();
			ResultSet generatedKey = stmt.getGeneratedKeys();
			generatedKey.next();
			conn.commit();

			sql = "UPDATE Ingredients, RecipeAmounts SET Ingredients.AmountInStorage = Ingredients.AmountInStorage - RecipeAmounts.Amount*54"
					+
					" WHERE Ingredients.IngredientType = RecipeAmounts.IngredientType AND RecipeAmounts.CookieType = '"
					+ cookie + "';";
			stmt = conn.prepareStatement(sql);
			stmt.executeUpdate();

			conn.commit();
			toJSON = "{\n \"status\": \"ok\",\n \"id\": " + generatedKey.getInt(1) + "\n }";
		} catch (SQLException e) {

			try {
				conn.rollback();
			} catch (SQLException e1) {
				e1.printStackTrace();
			}

			System.out.println("Insert new pallet failed");
			e.printStackTrace();
			return krusty.Jsonizer.anythingToJson("error", "status");

		} finally {

			try {
				conn.setAutoCommit(true);
				return toJSON;
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return krusty.Jsonizer.anythingToJson("error", "status");
	}
}