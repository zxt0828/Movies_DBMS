import org.jasypt.util.password.StrongPasswordEncryptor;

import java.sql.*;

/**
 * This program updates the existing plain text passwords in the customers table
 * to encrypted passwords using jasypt's StrongPasswordEncryptor.
 *
 * IMPORTANT: Run this ONCE only. Running it again will try to encrypt already-encrypted passwords.
 *
 * Usage: java -cp ".:mysql-connector-j-8.0.33.jar:jasypt-1.9.3.jar" UpdateSecurePassword
 */
public class UpdateSecurePassword {

    public static void main(String[] args) throws Exception {
        String loginUser = "mytestuser";
        String loginPasswd = "My6$Password";
        String loginUrl = "jdbc:mysql://localhost:3306/moviedb";

        Class.forName("com.mysql.cj.jdbc.Driver");
        Connection connection = DriverManager.getConnection(loginUrl, loginUser, loginPasswd);

        StrongPasswordEncryptor passwordEncryptor = new StrongPasswordEncryptor();

        // Update customers table
        System.out.println("Updating customers passwords...");
        String query = "SELECT id, password FROM customers";
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery(query);

        String updateQuery = "UPDATE customers SET password = ? WHERE id = ?";
        PreparedStatement updateStmt = connection.prepareStatement(updateQuery);

        int count = 0;
        while (rs.next()) {
            int id = rs.getInt("id");
            String plainPassword = rs.getString("password");
            String encryptedPassword = passwordEncryptor.encryptPassword(plainPassword);

            updateStmt.setString(1, encryptedPassword);
            updateStmt.setInt(2, id);
            updateStmt.executeUpdate();
            count++;
        }
        rs.close();
        statement.close();
        updateStmt.close();
        System.out.println("Updated " + count + " customer passwords.");

        // Update employees table
        System.out.println("Updating employee passwords...");
        query = "SELECT email, password FROM employees";
        statement = connection.createStatement();
        rs = statement.executeQuery(query);

        updateQuery = "UPDATE employees SET password = ? WHERE email = ?";
        updateStmt = connection.prepareStatement(updateQuery);

        count = 0;
        while (rs.next()) {
            String email = rs.getString("email");
            String plainPassword = rs.getString("password");
            String encryptedPassword = passwordEncryptor.encryptPassword(plainPassword);

            updateStmt.setString(1, encryptedPassword);
            updateStmt.setString(2, email);
            updateStmt.executeUpdate();
            count++;
        }
        rs.close();
        statement.close();
        updateStmt.close();
        System.out.println("Updated " + count + " employee passwords.");

        connection.close();
        System.out.println("Done! Passwords have been encrypted.");
    }
}
