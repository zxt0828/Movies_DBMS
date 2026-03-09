import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.sql.*;
import java.util.*;

/**
 * CS122B Project 3 - XML Parser
 * Parses Stanford movie XML files and imports data into the Fabflix moviedb database.
 *
 * Optimization techniques:
 *   1. Batch inserts - accumulate INSERT statements and execute in batches
 *   2. In-memory HashSets for duplicate detection - avoid querying DB for each record
 *
 * Usage: java -cp ".:mysql-connector-j-8.0.33.jar" XmlParser [path_to_xml_directory]
 */
public class XmlParser {

    private Connection conn;

    // In-memory caches for dedup (Optimization #2)
    private Set<String> existingMovieIds = new HashSet<>();
    private Map<String, String> existingMovieKeys = new HashMap<>(); // "title|year|director" -> movieId
    private Set<String> existingStarIds = new HashSet<>();
    private Map<String, String> existingStarNames = new HashMap<>(); // starName -> starId
    private Map<String, Integer> existingGenres = new HashMap<>(); // genreName -> genreId
    private Set<String> existingStarsInMovies = new HashSet<>(); // "starId|movieId"
    private Set<String> existingGenresInMovies = new HashSet<>(); // "genreId|movieId"

    // Mapping from XML film id (fid) to database movie id
    private Map<String, String> fidToMovieId = new HashMap<>();

    // Counters
    private int moviesInserted = 0, starsInserted = 0, genresInserted = 0;
    private int starsInMoviesInserted = 0, genresInMoviesInserted = 0;
    private int inconsistencies = 0;

    // ID generation
    private int nextMovieNum;
    private int nextStarNum;
    private int nextGenreId;

    // Batch lists
    private List<String[]> movieBatch = new ArrayList<>();
    private List<String[]> starBatch = new ArrayList<>();
    private List<Object[]> genreBatch = new ArrayList<>();
    private List<String[]> simBatch = new ArrayList<>(); // stars_in_movies
    private List<Object[]> gimBatch = new ArrayList<>(); // genres_in_movies

    private static final int BATCH_SIZE = 500;

    private PrintWriter inconsistencyLog;

    public static void main(String[] args) throws Exception {
        String xmlDir = args.length > 0 ? args[0] : ".";
        XmlParser parser = new XmlParser();
        long start = System.currentTimeMillis();
        parser.run(xmlDir);
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("\nTotal time: " + elapsed + " ms (" + (elapsed / 1000.0) + " seconds)");
    }

    public void run(String xmlDir) throws Exception {
        inconsistencyLog = new PrintWriter(new FileWriter("xml_parser_inconsistencies.txt"));

        // Connect to database
        String loginUser = "mytestuser";
        String loginPasswd = "My6$Password";
        String loginUrl = "jdbc:mysql://localhost:3306/moviedb?useSSL=false&allowPublicKeyRetrieval=true";

        Class.forName("com.mysql.cj.jdbc.Driver");
        conn = DriverManager.getConnection(loginUrl, loginUser, loginPasswd);
        conn.setAutoCommit(false); // Optimization: batch commits

        System.out.println("Connected to database.");

        // Load existing data into memory (Optimization #2)
        loadExistingData();

        // Parse actors63.xml first (to get star names)
        System.out.println("\n--- Parsing actors63.xml ---");
        parseActors(xmlDir + "/actors63.xml");

        // Parse mains243.xml (movies + genres)
        System.out.println("\n--- Parsing mains243.xml ---");
        parseMovies(xmlDir + "/mains243.xml");

        // Parse casts124.xml (stars_in_movies)
        System.out.println("\n--- Parsing casts124.xml ---");
        parseCasts(xmlDir + "/casts124.xml");

        // Flush remaining batches
        flushMovieBatch();
        flushStarBatch();
        flushGenreBatch();
        flushSimBatch();
        flushGimBatch();

        conn.commit();
        conn.close();

        System.out.println("\n=== Summary ===");
        System.out.println("Movies inserted: " + moviesInserted);
        System.out.println("Stars inserted: " + starsInserted);
        System.out.println("Genres inserted: " + genresInserted);
        System.out.println("Stars-in-movies inserted: " + starsInMoviesInserted);
        System.out.println("Genres-in-movies inserted: " + genresInMoviesInserted);
        System.out.println("Inconsistencies found: " + inconsistencies);
        System.out.println("Inconsistency log: xml_parser_inconsistencies.txt");

        inconsistencyLog.close();
    }

    private void loadExistingData() throws Exception {
        System.out.println("Loading existing data into memory...");

        // Movies
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SELECT id, title, year, director FROM movies");
        int maxMovieNum = 0;
        while (rs.next()) {
            String id = rs.getString("id");
            existingMovieIds.add(id);
            String key = rs.getString("title") + "|" + rs.getInt("year") + "|" + rs.getString("director");
            existingMovieKeys.put(key, id);
            try {
                int num = Integer.parseInt(id.replaceAll("[^0-9]", ""));
                if (num > maxMovieNum) maxMovieNum = num;
            } catch (NumberFormatException ignored) {}
        }
        rs.close();
        nextMovieNum = maxMovieNum + 1;

        // Stars
        rs = stmt.executeQuery("SELECT id, name FROM stars");
        int maxStarNum = 0;
        while (rs.next()) {
            String id = rs.getString("id");
            String name = rs.getString("name");
            existingStarIds.add(id);
            existingStarNames.put(name, id); // store first occurrence
            try {
                int num = Integer.parseInt(id.replaceAll("[^0-9]", ""));
                if (num > maxStarNum) maxStarNum = num;
            } catch (NumberFormatException ignored) {}
        }
        rs.close();
        nextStarNum = maxStarNum + 1;

        // Genres
        rs = stmt.executeQuery("SELECT id, name FROM genres");
        int maxGenreId = 0;
        while (rs.next()) {
            int id = rs.getInt("id");
            existingGenres.put(rs.getString("name"), id);
            if (id > maxGenreId) maxGenreId = id;
        }
        rs.close();
        nextGenreId = maxGenreId + 1;

        // Stars in movies
        rs = stmt.executeQuery("SELECT starId, movieId FROM stars_in_movies");
        while (rs.next()) {
            existingStarsInMovies.add(rs.getString("starId") + "|" + rs.getString("movieId"));
        }
        rs.close();

        // Genres in movies
        rs = stmt.executeQuery("SELECT genreId, movieId FROM genres_in_movies");
        while (rs.next()) {
            existingGenresInMovies.add(rs.getInt("genreId") + "|" + rs.getString("movieId"));
        }
        rs.close();
        stmt.close();

        System.out.println("Loaded: " + existingMovieIds.size() + " movies, " +
                existingStarIds.size() + " stars, " + existingGenres.size() + " genres");
    }

    private void parseActors(String filename) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new File(filename));
            doc.getDocumentElement().normalize();

            NodeList actorList = doc.getElementsByTagName("actor");
            System.out.println("Found " + actorList.getLength() + " actors in XML.");

            for (int i = 0; i < actorList.getLength(); i++) {
                Element actor = (Element) actorList.item(i);
                String stageName = getTextContent(actor, "stagename");
                String dob = getTextContent(actor, "dob");

                if (stageName == null || stageName.trim().isEmpty()) {
                    logInconsistency("actor", "empty stagename at index " + i);
                    continue;
                }
                stageName = stageName.trim();

                Integer birthYear = null;
                if (dob != null && !dob.trim().isEmpty()) {
                    try {
                        birthYear = Integer.parseInt(dob.trim());
                    } catch (NumberFormatException e) {
                        // Could be "n/a" or non-numeric; treat as null
                    }
                }

                // Only add if star doesn't already exist
                if (!existingStarNames.containsKey(stageName)) {
                    String newId = "nm" + String.format("%07d", nextStarNum++);
                    existingStarNames.put(stageName, newId);
                    existingStarIds.add(newId);
                    starBatch.add(new String[]{newId, stageName, birthYear == null ? null : birthYear.toString()});
                    if (starBatch.size() >= BATCH_SIZE) flushStarBatch();
                }
            }
            flushStarBatch();
        } catch (Exception e) {
            System.err.println("Error parsing actors: " + e.getMessage());
        }
    }

    private void parseMovies(String filename) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            // Parse with ISO-8859-1
            InputStreamReader isr = new InputStreamReader(new FileInputStream(filename), "ISO-8859-1");
            Document doc = builder.parse(new org.xml.sax.InputSource(isr));
            doc.getDocumentElement().normalize();

            NodeList directorFilmsList = doc.getElementsByTagName("directorfilms");
            System.out.println("Found " + directorFilmsList.getLength() + " director groups in XML.");

            for (int i = 0; i < directorFilmsList.getLength(); i++) {
                Element df = (Element) directorFilmsList.item(i);
                Element directorElem = (Element) df.getElementsByTagName("director").item(0);
                if (directorElem == null) continue;

                String directorName = getTextContent(directorElem, "dirname");
                if (directorName == null || directorName.trim().isEmpty()) {
                    directorName = getTextContent(directorElem, "dirn");
                }
                if (directorName == null || directorName.trim().isEmpty()) {
                    logInconsistency("director", "empty director name at index " + i);
                    continue;
                }
                directorName = directorName.trim();

                NodeList films = df.getElementsByTagName("film");
                for (int j = 0; j < films.getLength(); j++) {
                    Element film = (Element) films.item(j);
                    String fid = getTextContent(film, "fid");
                    String title = getTextContent(film, "t");
                    String yearStr = getTextContent(film, "year");

                    if (title == null || title.trim().isEmpty()) {
                        logInconsistency("film", "empty title, fid=" + fid);
                        continue;
                    }
                    title = title.trim();
                    if (title.length() > 100) title = title.substring(0, 100);

                    Integer year = null;
                    if (yearStr != null && !yearStr.trim().isEmpty()) {
                        try {
                            yearStr = yearStr.trim().replaceAll("[^0-9]", "");
                            if (!yearStr.isEmpty()) {
                                year = Integer.parseInt(yearStr);
                            }
                        } catch (NumberFormatException e) {
                            // non-numeric year
                        }
                    }

                    if (year == null) {
                        logInconsistency("film", "invalid year, fid=" + fid + ", title=" + title);
                        continue;
                    }

                    String movieKey = title + "|" + year + "|" + directorName;
                    String movieId;

                    if (existingMovieKeys.containsKey(movieKey)) {
                        movieId = existingMovieKeys.get(movieKey);
                    } else {
                        movieId = "tt" + String.format("%07d", nextMovieNum++);
                        existingMovieKeys.put(movieKey, movieId);
                        existingMovieIds.add(movieId);
                        movieBatch.add(new String[]{movieId, title, year.toString(), directorName});
                        if (movieBatch.size() >= BATCH_SIZE) flushMovieBatch();
                    }

                    if (fid != null && !fid.trim().isEmpty()) {
                        fidToMovieId.put(fid.trim(), movieId);
                    }

                    // Parse genres/categories
                    NodeList cats = film.getElementsByTagName("cat");
                    for (int k = 0; k < cats.getLength(); k++) {
                        String catName = cats.item(k).getTextContent();
                        if (catName == null || catName.trim().isEmpty()) continue;
                        catName = catName.trim();
                        if (catName.length() > 32) catName = catName.substring(0, 32);

                        int genreId;
                        if (existingGenres.containsKey(catName)) {
                            genreId = existingGenres.get(catName);
                        } else {
                            genreId = nextGenreId++;
                            existingGenres.put(catName, genreId);
                            genreBatch.add(new Object[]{genreId, catName});
                            if (genreBatch.size() >= BATCH_SIZE) flushGenreBatch();
                        }

                        String gimKey = genreId + "|" + movieId;
                        if (!existingGenresInMovies.contains(gimKey)) {
                            existingGenresInMovies.add(gimKey);
                            gimBatch.add(new Object[]{genreId, movieId});
                            if (gimBatch.size() >= BATCH_SIZE) flushGimBatch();
                        }
                    }
                }
            }
            flushMovieBatch();
            flushGenreBatch();
            flushGimBatch();
        } catch (Exception e) {
            System.err.println("Error parsing movies: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void parseCasts(String filename) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputStreamReader isr = new InputStreamReader(new FileInputStream(filename), "ISO-8859-1");
            Document doc = builder.parse(new org.xml.sax.InputSource(isr));
            doc.getDocumentElement().normalize();

            NodeList dirFilmsCasts = doc.getElementsByTagName("dirfilms");
            System.out.println("Found " + dirFilmsCasts.getLength() + " director groups in casts XML.");

            for (int i = 0; i < dirFilmsCasts.getLength(); i++) {
                Element dfc = (Element) dirFilmsCasts.item(i);
                NodeList mList = dfc.getElementsByTagName("m");

                for (int j = 0; j < mList.getLength(); j++) {
                    Element m = (Element) mList.item(j);
                    String fid = getTextContent(m, "f");
                    String actorName = getTextContent(m, "a");

                    if (fid == null || fid.trim().isEmpty()) {
                        logInconsistency("cast", "empty fid at index " + j);
                        continue;
                    }
                    fid = fid.trim();

                    if (actorName == null || actorName.trim().isEmpty()) {
                        logInconsistency("cast", "empty actor name, fid=" + fid);
                        continue;
                    }
                    actorName = actorName.trim();

                    String movieId = fidToMovieId.get(fid);
                    if (movieId == null) {
                        logInconsistency("cast", "unknown fid=" + fid + ", actor=" + actorName);
                        continue;
                    }

                    String starId = existingStarNames.get(actorName);
                    if (starId == null) {
                        // Create new star
                        starId = "nm" + String.format("%07d", nextStarNum++);
                        existingStarNames.put(actorName, starId);
                        existingStarIds.add(starId);
                        starBatch.add(new String[]{starId, actorName, null});
                        if (starBatch.size() >= BATCH_SIZE) flushStarBatch();
                    }

                    String simKey = starId + "|" + movieId;
                    if (!existingStarsInMovies.contains(simKey)) {
                        existingStarsInMovies.add(simKey);
                        simBatch.add(new String[]{starId, movieId});
                        if (simBatch.size() >= BATCH_SIZE) flushSimBatch();
                    }
                }
            }
            flushStarBatch();
            flushSimBatch();
        } catch (Exception e) {
            System.err.println("Error parsing casts: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ===== Batch flush methods (Optimization #1) =====

    private void flushMovieBatch() throws Exception {
        if (movieBatch.isEmpty()) return;
        String sql = "INSERT INTO movies (id, title, year, director) VALUES (?, ?, ?, ?)";
        PreparedStatement ps = conn.prepareStatement(sql);
        for (String[] row : movieBatch) {
            ps.setString(1, row[0]);
            ps.setString(2, row[1]);
            ps.setInt(3, Integer.parseInt(row[2]));
            ps.setString(4, row[3]);
            ps.addBatch();
        }
        ps.executeBatch();
        moviesInserted += movieBatch.size();
        movieBatch.clear();
        ps.close();
        conn.commit();
    }

    private void flushStarBatch() throws Exception {
        if (starBatch.isEmpty()) return;
        String sql = "INSERT INTO stars (id, name, birthYear) VALUES (?, ?, ?)";
        PreparedStatement ps = conn.prepareStatement(sql);
        for (String[] row : starBatch) {
            ps.setString(1, row[0]);
            ps.setString(2, row[1]);
            if (row[2] != null) {
                ps.setInt(3, Integer.parseInt(row[2]));
            } else {
                ps.setNull(3, Types.INTEGER);
            }
            ps.addBatch();
        }
        ps.executeBatch();
        starsInserted += starBatch.size();
        starBatch.clear();
        ps.close();
        conn.commit();
    }

    private void flushGenreBatch() throws Exception {
        if (genreBatch.isEmpty()) return;
        String sql = "INSERT INTO genres (id, name) VALUES (?, ?)";
        PreparedStatement ps = conn.prepareStatement(sql);
        for (Object[] row : genreBatch) {
            ps.setInt(1, (Integer) row[0]);
            ps.setString(2, (String) row[1]);
            ps.addBatch();
        }
        ps.executeBatch();
        genresInserted += genreBatch.size();
        genreBatch.clear();
        ps.close();
        conn.commit();
    }

    private void flushSimBatch() throws Exception {
        if (simBatch.isEmpty()) return;
        String sql = "INSERT INTO stars_in_movies (starId, movieId) VALUES (?, ?)";
        PreparedStatement ps = conn.prepareStatement(sql);
        for (String[] row : simBatch) {
            ps.setString(1, row[0]);
            ps.setString(2, row[1]);
            ps.addBatch();
        }
        ps.executeBatch();
        starsInMoviesInserted += simBatch.size();
        simBatch.clear();
        ps.close();
        conn.commit();
    }

    private void flushGimBatch() throws Exception {
        if (gimBatch.isEmpty()) return;
        String sql = "INSERT INTO genres_in_movies (genreId, movieId) VALUES (?, ?)";
        PreparedStatement ps = conn.prepareStatement(sql);
        for (Object[] row : gimBatch) {
            ps.setInt(1, (Integer) row[0]);
            ps.setString(2, (String) row[1]);
            ps.addBatch();
        }
        ps.executeBatch();
        genresInMoviesInserted += gimBatch.size();
        gimBatch.clear();
        ps.close();
        conn.commit();
    }

    // ===== Helper methods =====

    private String getTextContent(Element parent, String tagName) {
        NodeList nl = parent.getElementsByTagName(tagName);
        if (nl.getLength() == 0) return null;
        Node node = nl.item(0);
        if (node == null) return null;
        String text = node.getTextContent();
        return (text != null) ? text.trim() : null;
    }

    private void logInconsistency(String elementType, String details) {
        inconsistencies++;
        String msg = "INCONSISTENCY [" + elementType + "]: " + details;
        inconsistencyLog.println(msg);
        if (inconsistencies <= 20) {
            System.out.println("  WARNING: " + msg);
        } else if (inconsistencies == 21) {
            System.out.println("  (Suppressing further warnings, see log file)");
        }
    }
}
