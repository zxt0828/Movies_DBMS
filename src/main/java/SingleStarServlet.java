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
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@WebServlet(name = "SingleStarServlet", urlPatterns = "/api/single-star")
public class SingleStarServlet extends HttpServlet {

    @Resource(name = "jdbc/moviedb")
    private DataSource dataSource;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        String starId = request.getParameter("id");

        try (Connection conn = dataSource.getConnection()) {
            String query = "SELECT * FROM stars WHERE id = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, starId);
            ResultSet rs = stmt.executeQuery();

            JsonObject starObj = new JsonObject();
            if (rs.next()) {
                starObj.addProperty("star_id", rs.getString("id"));
                starObj.addProperty("star_name", rs.getString("name"));
                int birthYear = rs.getInt("birthYear");
                starObj.addProperty("star_birth_year", rs.wasNull() ? "N/A" : String.valueOf(birthYear));
            }
            rs.close();
            stmt.close();

            // Get movies this star is in
            String movieQuery = "SELECT m.id, m.title, m.year, m.director, r.rating " +
                    "FROM movies m JOIN stars_in_movies sim ON m.id = sim.movieId " +
                    "LEFT JOIN ratings r ON m.id = r.movieId " +
                    "WHERE sim.starId = ? ORDER BY m.year DESC, m.title ASC";
            PreparedStatement movieStmt = conn.prepareStatement(movieQuery);
            movieStmt.setString(1, starId);
            ResultSet movieRs = movieStmt.executeQuery();
            JsonArray movies = new JsonArray();
            while (movieRs.next()) {
                JsonObject m = new JsonObject();
                m.addProperty("movie_id", movieRs.getString("id"));
                m.addProperty("movie_title", movieRs.getString("title"));
                m.addProperty("movie_year", movieRs.getInt("year"));
                m.addProperty("movie_director", movieRs.getString("director"));
                m.addProperty("movie_rating", movieRs.getFloat("rating"));
                movies.add(m);
            }
            starObj.add("movies", movies);
            movieRs.close();
            movieStmt.close();

            out.write(starObj.toString());
        } catch (Exception e) {
            JsonObject error = new JsonObject();
            error.addProperty("status", "fail");
            error.addProperty("message", e.getMessage());
            out.write(error.toString());
        }
        out.close();
    }
}
