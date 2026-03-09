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
import java.sql.*;

@WebServlet(name = "DashboardServlet", urlPatterns = "/api/dashboard/*")
public class DashboardServlet extends HttpServlet {

    @Resource(name = "jdbc/moviedb")
    private DataSource dataSource;

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        String pathInfo = request.getPathInfo();

        if ("/metadata".equals(pathInfo)) {
            handleMetadata(out);
        } else {
            JsonObject error = new JsonObject();
            error.addProperty("status", "fail");
            error.addProperty("message", "Unknown endpoint");
            out.write(error.toString());
        }
        out.close();
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        PrintWriter out = response.getWriter();
        String pathInfo = request.getPathInfo();

        if ("/add-star".equals(pathInfo)) {
            handleAddStar(request, out);
        } else if ("/add-movie".equals(pathInfo)) {
            handleAddMovie(request, out);
        } else {
            JsonObject error = new JsonObject();
            error.addProperty("status", "fail");
            error.addProperty("message", "Unknown endpoint");
            out.write(error.toString());
        }
        out.close();
    }

    private void handleMetadata(PrintWriter out) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData dbMeta = conn.getMetaData();
            ResultSet tables = dbMeta.getTables("moviedb", null, null, new String[]{"TABLE"});

            JsonArray tablesArray = new JsonArray();
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");
                JsonObject tableObj = new JsonObject();
                tableObj.addProperty("table_name", tableName);

                ResultSet columns = dbMeta.getColumns("moviedb", null, tableName, null);
                JsonArray columnsArray = new JsonArray();
                while (columns.next()) {
                    JsonObject colObj = new JsonObject();
                    colObj.addProperty("column_name", columns.getString("COLUMN_NAME"));
                    colObj.addProperty("column_type", columns.getString("TYPE_NAME"));
                    colObj.addProperty("column_size", columns.getInt("COLUMN_SIZE"));
                    colObj.addProperty("is_nullable", columns.getString("IS_NULLABLE"));
                    columnsArray.add(colObj);
                }
                columns.close();
                tableObj.add("columns", columnsArray);
                tablesArray.add(tableObj);
            }
            tables.close();

            JsonObject result = new JsonObject();
            result.addProperty("status", "success");
            result.add("tables", tablesArray);
            out.write(result.toString());
        } catch (Exception e) {
            JsonObject error = new JsonObject();
            error.addProperty("status", "fail");
            error.addProperty("message", e.getMessage());
            out.write(error.toString());
        }
    }

    private void handleAddStar(HttpServletRequest request, PrintWriter out) {
        String starName = request.getParameter("starName");
        String birthYearStr = request.getParameter("birthYear");

        JsonObject responseObj = new JsonObject();

        if (starName == null || starName.trim().isEmpty()) {
            responseObj.addProperty("status", "fail");
            responseObj.addProperty("message", "Star name is required");
            out.write(responseObj.toString());
            return;
        }

        try (Connection conn = dataSource.getConnection()) {
            // Generate new star id
            String newId = generateNewStarId(conn);

            String query = "INSERT INTO stars (id, name, birthYear) VALUES (?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, newId);
            stmt.setString(2, starName.trim());
            if (birthYearStr != null && !birthYearStr.trim().isEmpty()) {
                stmt.setInt(3, Integer.parseInt(birthYearStr.trim()));
            } else {
                stmt.setNull(3, Types.INTEGER);
            }
            stmt.executeUpdate();
            stmt.close();

            responseObj.addProperty("status", "success");
            responseObj.addProperty("message", "Star '" + starName + "' added successfully with id: " + newId);
        } catch (Exception e) {
            responseObj.addProperty("status", "fail");
            responseObj.addProperty("message", "Error adding star: " + e.getMessage());
        }
        out.write(responseObj.toString());
    }

    private void handleAddMovie(HttpServletRequest request, PrintWriter out) {
        String title = request.getParameter("title");
        String yearStr = request.getParameter("year");
        String director = request.getParameter("director");
        String starName = request.getParameter("starName");
        String genreName = request.getParameter("genreName");

        JsonObject responseObj = new JsonObject();

        if (title == null || title.trim().isEmpty() || yearStr == null || yearStr.trim().isEmpty()
                || director == null || director.trim().isEmpty()
                || starName == null || starName.trim().isEmpty()
                || genreName == null || genreName.trim().isEmpty()) {
            responseObj.addProperty("status", "fail");
            responseObj.addProperty("message", "All fields are required: title, year, director, star name, genre name");
            out.write(responseObj.toString());
            return;
        }

        try (Connection conn = dataSource.getConnection()) {
            int year = Integer.parseInt(yearStr.trim());

            // Call stored procedure add_movie
            String callProc = "CALL add_movie(?, ?, ?, ?, ?, @movieMsg)";
            CallableStatement cstmt = conn.prepareCall(callProc);
            cstmt.setString(1, title.trim());
            cstmt.setInt(2, year);
            cstmt.setString(3, director.trim());
            cstmt.setString(4, starName.trim());
            cstmt.setString(5, genreName.trim());
            cstmt.execute();
            cstmt.close();

            // Get the output message
            Statement msgStmt = conn.createStatement();
            ResultSet msgRs = msgStmt.executeQuery("SELECT @movieMsg AS msg");
            String message = "Operation completed.";
            if (msgRs.next()) {
                message = msgRs.getString("msg");
            }
            msgRs.close();
            msgStmt.close();

            if (message != null && message.toLowerCase().contains("already exists")) {
                responseObj.addProperty("status", "fail");
                responseObj.addProperty("message", message);
            } else {
                responseObj.addProperty("status", "success");
                responseObj.addProperty("message", message != null ? message : "Movie added successfully.");
            }
        } catch (Exception e) {
            responseObj.addProperty("status", "fail");
            responseObj.addProperty("message", "Error adding movie: " + e.getMessage());
        }
        out.write(responseObj.toString());
    }

    private String generateNewStarId(Connection conn) throws Exception {
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT MAX(id) AS maxId FROM stars");
        String maxId = "nm0000000";
        if (rs.next() && rs.getString("maxId") != null) {
            maxId = rs.getString("maxId");
        }
        rs.close();
        stmt.close();

        // Parse numeric part and increment
        String numPart = maxId.replaceAll("[^0-9]", "");
        int nextNum = Integer.parseInt(numPart) + 1;
        return "nm" + String.format("%07d", nextNum);
    }
}
