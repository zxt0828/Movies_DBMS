import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.annotation.Resource;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

@WebServlet(name = "CheckoutServlet", urlPatterns = "/api/checkout")
public class CheckoutServlet extends HttpServlet {

    @Resource(name = "jdbc/moviedb")
    private DataSource dataSource;

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        String firstName = request.getParameter("firstName");
        String lastName = request.getParameter("lastName");
        String creditCard = request.getParameter("creditCard");
        String expiration = request.getParameter("expiration");

        HttpSession session = request.getSession();
        User user = (User) session.getAttribute("user");
        @SuppressWarnings("unchecked")
        Map<String, Integer> cart = (Map<String, Integer>) session.getAttribute("cart");

        JsonObject responseObj = new JsonObject();

        if (cart == null || cart.isEmpty()) {
            responseObj.addProperty("status", "fail");
            responseObj.addProperty("message", "Shopping cart is empty");
            out.write(responseObj.toString());
            out.close();
            return;
        }

        try (Connection conn = dataSource.getConnection()) {
            // Verify credit card in creditcards table
            String ccQuery = "SELECT * FROM creditcards WHERE id = ? AND firstName = ? AND lastName = ? AND expiration = ?";
            PreparedStatement ccStmt = conn.prepareStatement(ccQuery);
            ccStmt.setString(1, creditCard);
            ccStmt.setString(2, firstName);
            ccStmt.setString(3, lastName);
            ccStmt.setString(4, expiration);
            ResultSet ccRs = ccStmt.executeQuery();

            if (!ccRs.next()) {
                responseObj.addProperty("status", "fail");
                responseObj.addProperty("message", "Invalid credit card information. Please check and try again.");
                ccRs.close();
                ccStmt.close();
                out.write(responseObj.toString());
                out.close();
                return;
            }
            ccRs.close();
            ccStmt.close();

            // Insert sales records
            JsonArray salesConfirmation = new JsonArray();
            String insertSale = "INSERT INTO sales (customerId, movieId, saleDate) VALUES (?, ?, CURDATE())";

            for (Map.Entry<String, Integer> entry : cart.entrySet()) {
                String movieId = entry.getKey();
                int quantity = entry.getValue();

                for (int i = 0; i < quantity; i++) {
                    PreparedStatement insertStmt = conn.prepareStatement(insertSale);
                    insertStmt.setInt(1, user.getId());
                    insertStmt.setString(2, movieId);
                    insertStmt.executeUpdate();
                    insertStmt.close();
                }

                // Get movie title for confirmation
                PreparedStatement titleStmt = conn.prepareStatement("SELECT title FROM movies WHERE id = ?");
                titleStmt.setString(1, movieId);
                ResultSet titleRs = titleStmt.executeQuery();
                if (titleRs.next()) {
                    JsonObject saleItem = new JsonObject();
                    saleItem.addProperty("movie_title", titleRs.getString("title"));
                    saleItem.addProperty("quantity", quantity);
                    saleItem.addProperty("subtotal", 9.99 * quantity);
                    salesConfirmation.add(saleItem);
                }
                titleRs.close();
                titleStmt.close();
            }

            // Clear cart
            session.setAttribute("cart", new HashMap<String, Integer>());

            responseObj.addProperty("status", "success");
            responseObj.addProperty("message", "Transaction completed successfully!");
            responseObj.add("sales", salesConfirmation);
            out.write(responseObj.toString());
        } catch (Exception e) {
            responseObj.addProperty("status", "fail");
            responseObj.addProperty("message", "Error processing checkout: " + e.getMessage());
            out.write(responseObj.toString());
        }
        out.close();
    }
}
