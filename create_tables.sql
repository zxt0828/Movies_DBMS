-- ============================================
-- CS122B Project 3 SQL Setup Script
-- Run this on your MySQL server BEFORE deploying
-- ============================================

-- Task 6 Step 1: Create employees table
CREATE TABLE IF NOT EXISTS employees (
    email VARCHAR(50) PRIMARY KEY,
    password VARCHAR(128) NOT NULL,
    fullname VARCHAR(100)
);

-- Task 6 Step 2: Insert employee (plain text password - will be encrypted by UpdateSecurePassword.java)
-- NOTE: If you've already encrypted, you'll need to insert the encrypted version directly
INSERT IGNORE INTO employees (email, password, fullname)
VALUES ('classta@email.edu', 'classta', 'TA CS122B');

-- Alter employees table to allow longer encrypted passwords
ALTER TABLE employees MODIFY password VARCHAR(128) NOT NULL;

-- Alter customers table to allow longer encrypted passwords
ALTER TABLE customers MODIFY password VARCHAR(128) NOT NULL;

-- ============================================
-- Task 6 Step 5: Stored Procedure add_movie
-- ============================================

DROP PROCEDURE IF EXISTS add_movie;

DELIMITER $$

CREATE PROCEDURE add_movie(
    IN p_title VARCHAR(100),
    IN p_year INT,
    IN p_director VARCHAR(100),
    IN p_star_name VARCHAR(100),
    IN p_genre_name VARCHAR(32),
    OUT p_message VARCHAR(500)
)
BEGIN
    DECLARE v_movie_id VARCHAR(10);
    DECLARE v_star_id VARCHAR(10);
    DECLARE v_genre_id INT;
    DECLARE v_movie_exists INT DEFAULT 0;
    DECLARE v_max_id VARCHAR(10);
    DECLARE v_num INT;

    -- Check if movie already exists (identified by title, year, director)
    SELECT COUNT(*) INTO v_movie_exists
    FROM movies
    WHERE title = p_title AND year = p_year AND director = p_director;

    IF v_movie_exists > 0 THEN
        SET p_message = CONCAT('Movie "', p_title, '" (', p_year, ') by ', p_director, ' already exists. No changes made.');
    ELSE
        -- Generate new movie id
        SELECT MAX(id) INTO v_max_id FROM movies;
        IF v_max_id IS NULL THEN
            SET v_movie_id = 'tt0000001';
        ELSE
            SET v_num = CAST(SUBSTRING(v_max_id, 3) AS UNSIGNED) + 1;
            SET v_movie_id = CONCAT('tt', LPAD(v_num, 7, '0'));
        END IF;

        -- Insert the movie
        INSERT INTO movies (id, title, year, director) VALUES (v_movie_id, p_title, p_year, p_director);

        -- Handle genre: find existing or create new
        SELECT id INTO v_genre_id FROM genres WHERE name = p_genre_name LIMIT 1;
        IF v_genre_id IS NULL THEN
            INSERT INTO genres (name) VALUES (p_genre_name);
            SET v_genre_id = LAST_INSERT_ID();
        END IF;

        -- Link movie to genre
        INSERT INTO genres_in_movies (genreId, movieId) VALUES (v_genre_id, v_movie_id);

        -- Handle star: find existing or create new
        SELECT id INTO v_star_id FROM stars WHERE name = p_star_name LIMIT 1;
        IF v_star_id IS NULL THEN
            -- Generate new star id
            SELECT MAX(id) INTO v_max_id FROM stars;
            IF v_max_id IS NULL THEN
                SET v_star_id = 'nm0000001';
            ELSE
                SET v_num = CAST(SUBSTRING(v_max_id, 3) AS UNSIGNED) + 1;
                SET v_star_id = CONCAT('nm', LPAD(v_num, 7, '0'));
            END IF;
            INSERT INTO stars (id, name) VALUES (v_star_id, p_star_name);
        END IF;

        -- Link movie to star
        INSERT INTO stars_in_movies (starId, movieId) VALUES (v_star_id, v_movie_id);

        SET p_message = CONCAT('Successfully added movie "', p_title, '" (id: ', v_movie_id,
            '), linked to star "', p_star_name, '" (id: ', v_star_id,
            ') and genre "', p_genre_name, '" (id: ', v_genre_id, ').');
    END IF;
END$$

DELIMITER ;
