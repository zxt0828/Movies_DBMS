import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.annotation.Resource;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@WebServlet(name = "GenreServlet", urlPatterns = "/api/genres")
public class GenreServlet extends HttpServlet {

    @Resource(name = "jdbc/moviedb")
    private DataSource dataSource;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        try (Connection conn = dataSource.getConnection()) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM genres ORDER BY name");

            JsonArray genres = new JsonArray();
            while (rs.next()) {
                JsonObject genre = new JsonObject();
                genre.addProperty("genre_id", rs.getInt("id"));
                genre.addProperty("genre_name", rs.getString("name"));
                genres.add(genre);
            }
            rs.close();
            stmt.close();
            out.write(genres.toString());
        } catch (Exception e) {
            JsonObject error = new JsonObject();
            error.addProperty("status", "fail");
            error.addProperty("message", e.getMessage());
            out.write(error.toString());
        }
        out.close();
    }
}
