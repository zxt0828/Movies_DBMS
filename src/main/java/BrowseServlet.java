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

@WebServlet(name = "BrowseServlet", urlPatterns = "/api/browse")
public class BrowseServlet extends HttpServlet {

    @Resource(name = "jdbc/moviedb")
    private DataSource dataSource;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();

        String genreId = request.getParameter("genreId");
        String titleInitial = request.getParameter("titleInitial");
        String sortBy = request.getParameter("sortBy");
        String limitStr = request.getParameter("limit");
        String pageStr = request.getParameter("page");

        int limit = (limitStr != null && !limitStr.isEmpty()) ? Integer.parseInt(limitStr) : 25;
        int page = (pageStr != null && !pageStr.isEmpty()) ? Integer.parseInt(pageStr) : 1;
        int offset = (page - 1) * limit;

        try (Connection conn = dataSource.getConnection()) {
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("SELECT DISTINCT m.id, m.title, m.year, m.director, r.rating ");
            queryBuilder.append("FROM movies m ");
            queryBuilder.append("LEFT JOIN ratings r ON m.id = r.movieId ");

            if (genreId != null && !genreId.isEmpty()) {
                queryBuilder.append("JOIN genres_in_movies gim ON m.id = gim.movieId ");
                queryBuilder.append("WHERE gim.genreId = ? ");
            } else if (titleInitial != null && !titleInitial.isEmpty()) {
                if (titleInitial.equals("*")) {
                    queryBuilder.append("WHERE m.title REGEXP '^[^a-zA-Z0-9]' ");
                } else {
                    queryBuilder.append("WHERE m.title LIKE ? ");
                }
            }

            // Sorting
            String orderClause = getOrderClause(sortBy);
            queryBuilder.append(orderClause);

            // Count total
            String countQuery = "SELECT COUNT(*) AS total FROM (" + queryBuilder.toString() + ") AS countTable";

            queryBuilder.append(" LIMIT ? OFFSET ?");

            // Execute count
            PreparedStatement countStmt = conn.prepareStatement(countQuery);
            int paramIndex = 1;
            if (genreId != null && !genreId.isEmpty()) {
                countStmt.setInt(paramIndex++, Integer.parseInt(genreId));
            } else if (titleInitial != null && !titleInitial.isEmpty() && !titleInitial.equals("*")) {
                countStmt.setString(paramIndex++, titleInitial + "%");
            }
            ResultSet countRs = countStmt.executeQuery();
            int totalResults = 0;
            if (countRs.next()) totalResults = countRs.getInt("total");
            countRs.close();
            countStmt.close();

            // Execute main query
            PreparedStatement stmt = conn.prepareStatement(queryBuilder.toString());
            paramIndex = 1;
            if (genreId != null && !genreId.isEmpty()) {
                stmt.setInt(paramIndex++, Integer.parseInt(genreId));
            } else if (titleInitial != null && !titleInitial.isEmpty() && !titleInitial.equals("*")) {
                stmt.setString(paramIndex++, titleInitial + "%");
            }
            stmt.setInt(paramIndex++, limit);
            stmt.setInt(paramIndex, offset);

            ResultSet rs = stmt.executeQuery();
            JsonArray movieArray = new JsonArray();
            while (rs.next()) {
                String movieId = rs.getString("id");
                JsonObject movieObj = new JsonObject();
                movieObj.addProperty("movie_id", movieId);
                movieObj.addProperty("movie_title", rs.getString("title"));
                movieObj.addProperty("movie_year", rs.getInt("year"));
                movieObj.addProperty("movie_director", rs.getString("director"));
                movieObj.addProperty("movie_rating", rs.getFloat("rating"));
                movieObj.add("movie_genres", getGenres(conn, movieId));
                movieObj.add("movie_stars", getStars(conn, movieId));
                movieArray.add(movieObj);
            }
            rs.close();
            stmt.close();

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
        String query = "SELECT g.id, g.name FROM genres g JOIN genres_in_movies gim ON g.id = gim.genreId WHERE gim.movieId = ? ORDER BY g.name LIMIT 3";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, movieId);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            JsonObject g = new JsonObject();
            g.addProperty("genre_id", rs.getInt("id"));
            g.addProperty("genre_name", rs.getString("name"));
            genres.add(g);
        }
        rs.close(); stmt.close();
        return genres;
    }

    private JsonArray getStars(Connection conn, String movieId) throws Exception {
        JsonArray stars = new JsonArray();
        String query = "SELECT s.id, s.name FROM stars s JOIN stars_in_movies sim ON s.id = sim.starId WHERE sim.movieId = ? ORDER BY (SELECT COUNT(*) FROM stars_in_movies WHERE starId = s.id) DESC, s.name ASC LIMIT 3";
        PreparedStatement stmt = conn.prepareStatement(query);
        stmt.setString(1, movieId);
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            JsonObject starObj = new JsonObject();
            starObj.addProperty("star_id", rs.getString("id"));
            starObj.addProperty("star_name", rs.getString("name"));
            stars.add(starObj);
        }
        rs.close(); stmt.close();
        return stars;
    }

    private String getOrderClause(String sortBy) {
        if (sortBy == null || sortBy.isEmpty()) return "ORDER BY r.rating DESC, m.title ASC ";
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
