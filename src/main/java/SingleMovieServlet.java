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

@WebServlet(name = "SingleMovieServlet", urlPatterns = "/api/single-movie")
public class SingleMovieServlet extends HttpServlet {

    @Resource(name = "jdbc/moviedb")
    private DataSource dataSource;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        String movieId = request.getParameter("id");

        try (Connection conn = dataSource.getConnection()) {
            // Get movie info
            String query = "SELECT m.*, r.rating FROM movies m LEFT JOIN ratings r ON m.id = r.movieId WHERE m.id = ?";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, movieId);
            ResultSet rs = stmt.executeQuery();

            JsonObject movieObj = new JsonObject();
            if (rs.next()) {
                movieObj.addProperty("movie_id", rs.getString("id"));
                movieObj.addProperty("movie_title", rs.getString("title"));
                movieObj.addProperty("movie_year", rs.getInt("year"));
                movieObj.addProperty("movie_director", rs.getString("director"));
                movieObj.addProperty("movie_rating", rs.getFloat("rating"));
            }
            rs.close();
            stmt.close();

            // Get all genres
            String genreQuery = "SELECT g.id, g.name FROM genres g JOIN genres_in_movies gim ON g.id = gim.genreId WHERE gim.movieId = ? ORDER BY g.name";
            PreparedStatement genreStmt = conn.prepareStatement(genreQuery);
            genreStmt.setString(1, movieId);
            ResultSet genreRs = genreStmt.executeQuery();
            JsonArray genres = new JsonArray();
            while (genreRs.next()) {
                JsonObject g = new JsonObject();
                g.addProperty("genre_id", genreRs.getInt("id"));
                g.addProperty("genre_name", genreRs.getString("name"));
                genres.add(g);
            }
            movieObj.add("movie_genres", genres);
            genreRs.close();
            genreStmt.close();

            // Get all stars
            String starQuery = "SELECT s.id, s.name, s.birthYear FROM stars s JOIN stars_in_movies sim ON s.id = sim.starId WHERE sim.movieId = ? ORDER BY (SELECT COUNT(*) FROM stars_in_movies WHERE starId = s.id) DESC, s.name ASC";
            PreparedStatement starStmt = conn.prepareStatement(starQuery);
            starStmt.setString(1, movieId);
            ResultSet starRs = starStmt.executeQuery();
            JsonArray stars = new JsonArray();
            while (starRs.next()) {
                JsonObject s = new JsonObject();
                s.addProperty("star_id", starRs.getString("id"));
                s.addProperty("star_name", starRs.getString("name"));
                int birthYear = starRs.getInt("birthYear");
                s.addProperty("star_birth_year", starRs.wasNull() ? "N/A" : String.valueOf(birthYear));
                stars.add(s);
            }
            movieObj.add("movie_stars", stars);
            starRs.close();
            starStmt.close();

            out.write(movieObj.toString());
        } catch (Exception e) {
            JsonObject error = new JsonObject();
            error.addProperty("status", "fail");
            error.addProperty("message", e.getMessage());
            out.write(error.toString());
        }
        out.close();
    }
}
