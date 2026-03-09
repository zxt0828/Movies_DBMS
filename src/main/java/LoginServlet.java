import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jasypt.util.password.StrongPasswordEncryptor;

import javax.annotation.Resource;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@WebServlet(name = "LoginServlet", urlPatterns = "/api/login")
public class LoginServlet extends HttpServlet {

    private static final String RECAPTCHA_SECRET_KEY = "6LdVKoIsAAAALODWTRA8zpYFmWl4Osh55VNPSDh";
    private static final String RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    @Resource(name = "jdbc/moviedb")
    private DataSource dataSource;

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String gRecaptchaResponse = request.getParameter("g-recaptcha-response");

        JsonObject responseJsonObject = new JsonObject();

        // Verify reCAPTCHA
        try {
            if (gRecaptchaResponse == null || gRecaptchaResponse.isEmpty()) {
                responseJsonObject.addProperty("status", "fail");
                responseJsonObject.addProperty("message", "Please complete the reCAPTCHA verification.");
                out.write(responseJsonObject.toString());
                out.close();
                return;
            }

            if (!verifyRecaptcha(gRecaptchaResponse)) {
                responseJsonObject.addProperty("status", "fail");
                responseJsonObject.addProperty("message", "reCAPTCHA verification failed. Please try again.");
                out.write(responseJsonObject.toString());
                out.close();
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try (Connection conn = dataSource.getConnection()) {
            String query = "SELECT * FROM customers WHERE email = ?";
            PreparedStatement statement = conn.prepareStatement(query);
            statement.setString(1, email);
            ResultSet rs = statement.executeQuery();

            if (rs.next()) {
                String encryptedPassword = rs.getString("password");
                StrongPasswordEncryptor passwordEncryptor = new StrongPasswordEncryptor();
                boolean passwordMatch = passwordEncryptor.checkPassword(password, encryptedPassword);

                if (passwordMatch) {
                    HttpSession session = request.getSession();
                    session.setAttribute("user", new User(rs.getInt("id"), rs.getString("firstName"),
                            rs.getString("lastName"), rs.getString("email")));
                    responseJsonObject.addProperty("status", "success");
                    responseJsonObject.addProperty("message", "Login successful");
                } else {
                    responseJsonObject.addProperty("status", "fail");
                    responseJsonObject.addProperty("message", "Incorrect email or password");
                }
            } else {
                responseJsonObject.addProperty("status", "fail");
                responseJsonObject.addProperty("message", "Incorrect email or password");
            }
            rs.close();
            statement.close();
        } catch (Exception e) {
            responseJsonObject.addProperty("status", "fail");
            responseJsonObject.addProperty("message", "Internal server error: " + e.getMessage());
        }

        out.write(responseJsonObject.toString());
        out.close();
    }

    private boolean verifyRecaptcha(String gRecaptchaResponse) {
        try {
            URL url = new URL(RECAPTCHA_VERIFY_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            String postParams = "secret=" + RECAPTCHA_SECRET_KEY + "&response=" + gRecaptchaResponse;
            OutputStream os = conn.getOutputStream();
            os.write(postParams.getBytes());
            os.flush();
            os.close();

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            br.close();

            JsonObject jsonObject = JsonParser.parseString(sb.toString()).getAsJsonObject();
            return jsonObject.get("success").getAsBoolean();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
