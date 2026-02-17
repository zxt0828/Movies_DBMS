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

@WebServlet(name = "CartServlet", urlPatterns = "/api/cart")
public class CartServlet extends HttpServlet {

    @Resource(name = "jdbc/moviedb")
    private DataSource dataSource;

    // GET: retrieve cart contents
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        HttpSession session = request.getSession();
        @SuppressWarnings("unchecked")
        Map<String, Integer> cart = (Map<String, Integer>) session.getAttribute("cart");
        if (cart == null) cart = new HashMap<>();

        try (Connection conn = dataSource.getConnection()) {
            JsonArray cartItems = new JsonArray();
            double totalPrice = 0;
            for (Map.Entry<String, Integer> entry : cart.entrySet()) {
                String movieId = entry.getKey();
                int quantity = entry.getValue();
                String query = "SELECT m.id, m.title, m.year FROM movies m WHERE m.id = ?";
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setString(1, movieId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    JsonObject item = new JsonObject();
                    item.addProperty("movie_id", rs.getString("id"));
                    item.addProperty("movie_title", rs.getString("title"));
                    item.addProperty("movie_year", rs.getInt("year"));
                    item.addProperty("quantity", quantity);
                    item.addProperty("price", 9.99);
                    item.addProperty("subtotal", 9.99 * quantity);
                    cartItems.add(item);
                    totalPrice += 9.99 * quantity;
                }
                rs.close();
                stmt.close();
            }

            JsonObject result = new JsonObject();
            result.add("items", cartItems);
            result.addProperty("totalPrice", Math.round(totalPrice * 100.0) / 100.0);
            out.write(result.toString());
        } catch (Exception e) {
            JsonObject error = new JsonObject();
            error.addProperty("status", "fail");
            error.addProperty("message", e.getMessage());
            out.write(error.toString());
        }
        out.close();
    }

    // POST: add/update/remove items
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        HttpSession session = request.getSession();
        @SuppressWarnings("unchecked")
        Map<String, Integer> cart = (Map<String, Integer>) session.getAttribute("cart");
        if (cart == null) cart = new HashMap<>();

        String movieId = request.getParameter("movieId");
        String action = request.getParameter("action"); // "add", "update", "remove"
        String quantityStr = request.getParameter("quantity");

        JsonObject responseObj = new JsonObject();

        try {
            if ("add".equals(action)) {
                cart.put(movieId, cart.getOrDefault(movieId, 0) + 1);
                responseObj.addProperty("status", "success");
                responseObj.addProperty("message", "Item added to cart");
            } else if ("update".equals(action)) {
                int quantity = Integer.parseInt(quantityStr);
                if (quantity <= 0) {
                    cart.remove(movieId);
                } else {
                    cart.put(movieId, quantity);
                }
                responseObj.addProperty("status", "success");
                responseObj.addProperty("message", "Cart updated");
            } else if ("remove".equals(action)) {
                cart.remove(movieId);
                responseObj.addProperty("status", "success");
                responseObj.addProperty("message", "Item removed from cart");
            } else {
                responseObj.addProperty("status", "fail");
                responseObj.addProperty("message", "Invalid action");
            }

            session.setAttribute("cart", cart);
            responseObj.addProperty("cartSize", cart.values().stream().mapToInt(Integer::intValue).sum());
        } catch (Exception e) {
            responseObj.addProperty("status", "fail");
            responseObj.addProperty("message", e.getMessage());
        }

        out.write(responseObj.toString());
        out.close();
    }
}
