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
import java.util.ArrayList;
import java.util.List;

@WebServlet(name = "SearchServlet", urlPatterns = "/api/search")
public class SearchServlet extends HttpServlet {

    @Resource(name = "jdbc/moviedb")
    private DataSource dataSource;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        String title = request.getParameter("title");
        String year = request.getParameter("year");
        String director = request.getParameter("director");
        String star = request.getParameter("star");
        String sortBy = request.getParameter("sortBy");         // e.g. "title_asc", "rating_desc"
        String limitStr = request.getParameter("limit");
        String pageStr = request.getParameter("page");

        int limit = (limitStr != null && !limitStr.isEmpty()) ? Integer.parseInt(limitStr) : 25;
        int page = (pageStr != null && !pageStr.isEmpty()) ? Integer.parseInt(pageStr) : 1;
        int offset = (page - 1) * limit;

        try (Connection conn = dataSource.getConnection()) {
            // Build the query dynamically
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("SELECT DISTINCT m.id, m.title, m.year, m.director, r.rating ");
            queryBuilder.append("FROM movies m ");
            queryBuilder.append("LEFT JOIN ratings r ON m.id = r.movieId ");

            List<String> conditions = new ArrayList<>();
            List<String> params = new ArrayList<>();

            if (star != null && !star.isEmpty()) {
                queryBuilder.append("JOIN stars_in_movies sim ON m.id = sim.movieId ");
                queryBuilder.append("JOIN stars s ON sim.starId = s.id ");
                conditions.add("s.name LIKE ?");
                params.add("%" + star + "%");
            }

            if (title != null && !title.isEmpty()) {
                conditions.add("m.title LIKE ?");
                params.add("%" + title + "%");
            }
            if (year != null && !year.isEmpty()) {
                conditions.add("m.year = ?");
                params.add(year);
            }
            if (director != null && !director.isEmpty()) {
                conditions.add("m.director LIKE ?");
                params.add("%" + director + "%");
            }

            if (!conditions.isEmpty()) {
                queryBuilder.append("WHERE ").append(String.join(" AND ", conditions)).append(" ");
            }

            // Sorting
            String orderClause = getOrderClause(sortBy);
            queryBuilder.append(orderClause);

            // Count total for pagination
            String countQuery = "SELECT COUNT(*) AS total FROM (" + queryBuilder.toString() + ") AS countTable";

            // Add LIMIT and OFFSET
            queryBuilder.append(" LIMIT ? OFFSET ?");

            // Execute count query
            PreparedStatement countStmt = conn.prepareStatement(countQuery);
            for (int i = 0; i < params.size(); i++) {
                countStmt.setString(i + 1, params.get(i));
            }
            ResultSet countRs = countStmt.executeQuery();
            int totalResults = 0;
            if (countRs.next()) {
                totalResults = countRs.getInt("total");
            }
            countRs.close();
            countStmt.close();

            // Execute main query
            PreparedStatement statement = conn.prepareStatement(queryBuilder.toString());
            for (int i = 0; i < params.size(); i++) {
                statement.setString(i + 1, params.get(i));
            }
            statement.setInt(params.size() + 1, limit);
            statement.setInt(params.size() + 2, offset);

            ResultSet rs = statement.executeQuery();

            JsonArray movieArray = new JsonArray();
            while (rs.next()) {
                String movieId = rs.getString("id");
                JsonObject movieObj = new JsonObject();
                movieObj.addProperty("movie_id", movieId);
                movieObj.addProperty("movie_title", rs.getString("title"));
                movieObj.addProperty("movie_year", rs.getInt("year"));
                movieObj.addProperty("movie_director", rs.getString("director"));
                movieObj.addProperty("movie_rating", rs.getFloat("rating"));

                // Get genres for this movie (limit 3)
                movieObj.add("movie_genres", getGenres(conn, movieId));
                // Get stars for this movie (limit 3)
                movieObj.add("movie_stars", getStars(conn, movieId));

                movieArray.add(movieObj);
            }
            rs.close();
            statement.close();

            JsonObject result = new JsonObject();
            result.add("movies", movieArray);
            result.addProperty("totalResults", totalResults);
            result.addProperty("page", page);
            result.addProperty("limit", limit);
            result.addProperty("totalPages", (int) Math.ceil((double) totalResults / limit));

            out.write(result.toString());
        } catch (Exception e) {
            JsonObject errorObj = new JsonObject();
            errorObj.addProperty("status", "fail");
            errorObj.addProperty("message", e.getMessage());
            out.write(errorObj.toString());
        }
        out.close();
    }

    private JsonArray getGenres(Connection conn, String movieId) throws Exception {
        JsonArray genres = new JsonArray();
        String query = "SELECT g.id, g.name FROM genres g " +
                "JOIN genres_in_movies gim ON g.id = gim.genreId " +
                "WHERE gim.movieId = ? ORDER BY g.name LIMIT 3";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, movieId);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            JsonObject genre = new JsonObject();
            genre.addProperty("genre_id", rs.getInt("id"));
            genre.addProperty("genre_name", rs.getString("name"));
            genres.add(genre);
        }
        rs.close();
        stmt.close();
        return genres;
    }

    private JsonArray getStars(Connection conn, String movieId) throws Exception {
        JsonArray stars = new JsonArray();
        String query = "SELECT s.id, s.name FROM stars s " +
                "JOIN stars_in_movies sim ON s.id = sim.starId " +
                "WHERE sim.movieId = ? " +
                "ORDER BY (SELECT COUNT(*) FROM stars_in_movies WHERE starId = s.id) DESC, s.name ASC LIMIT 3";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, movieId);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            JsonObject starObj = new JsonObject();
            starObj.addProperty("star_id", rs.getString("id"));
            starObj.addProperty("star_name", rs.getString("name"));
            stars.add(starObj);
        }
        rs.close();
        stmt.close();
        return stars;
    }

    private String getOrderClause(String sortBy) {
        if (sortBy == null || sortBy.isEmpty()) {
            return "ORDER BY r.rating DESC, m.title ASC ";
        }
        switch (sortBy) {
            case "title_asc_rating_asc": return "ORDER BY m.title ASC, r.rating ASC ";
            case "title_asc_rating_desc": return "ORDER BY m.title ASC, r.rating DESC ";
            case "title_desc_rating_asc": return "ORDER BY m.title DESC, r.rating ASC ";
            case "title_desc_rating_desc": return "ORDER BY m.title DESC, r.rating DESC ";
            case "rating_asc_title_asc": return "ORDER BY r.rating ASC, m.title ASC ";
            case "rating_asc_title_desc": return "ORDER BY r.rating ASC, m.title DESC ";
            case "rating_desc_title_asc": return "ORDER BY r.rating DESC, m.title ASC ";
            case "rating_desc_title_desc": return "ORDER BY r.rating DESC, m.title DESC ";
            default: return "ORDER BY r.rating DESC, m.title ASC ";
        }
    }
}
