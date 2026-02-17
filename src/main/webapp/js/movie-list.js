$(document).ready(function() {
    var urlParams = new URLSearchParams(window.location.search);
    var currentPage = parseInt(urlParams.get("page")) || 1;
    var currentLimit = parseInt(urlParams.get("limit")) || 25;
    var currentSort = urlParams.get("sortBy") || "rating_desc_title_asc";
    var totalPages = 1;

    // Set controls to current values
    $("#sort-select").val(currentSort);
    $("#limit-select").val(currentLimit);

    function loadMovies() {
        var apiUrl;
        var params = {};

        if (urlParams.has("genreId")) {
            apiUrl = "../api/browse";
            params.genreId = urlParams.get("genreId");
        } else if (urlParams.has("titleInitial")) {
            apiUrl = "../api/browse";
            params.titleInitial = urlParams.get("titleInitial");
        } else {
            apiUrl = "../api/search";
            if (urlParams.has("title")) params.title = urlParams.get("title");
            if (urlParams.has("year")) params.year = urlParams.get("year");
            if (urlParams.has("director")) params.director = urlParams.get("director");
            if (urlParams.has("star")) params.star = urlParams.get("star");
        }

        params.sortBy = currentSort;
        params.limit = currentLimit;
        params.page = currentPage;

        $.ajax({
            url: apiUrl,
            method: "GET",
            data: params,
            success: function(data) {
                if (typeof data === "string") data = JSON.parse(data);
                totalPages = data.totalPages || 1;

                $("#result-info").text("Found " + data.totalResults + " movies (Page " + currentPage + " of " + totalPages + ")");
                $("#page-info").text("Page " + currentPage + " of " + totalPages);

                var tbody = $("#movie-table-body");
                tbody.empty();

                if (!data.movies || data.movies.length === 0) {
                    tbody.append('<tr><td colspan="8" style="text-align:center; color:#aaa;">No movies found.</td></tr>');
                    return;
                }

                data.movies.forEach(function(movie, index) {
                    var genreHtml = "";
                    if (movie.movie_genres) {
                        movie.movie_genres.forEach(function(g, i) {
                            if (i > 0) genreHtml += ", ";
                            genreHtml += '<a href="movie-list.html?genreId=' + g.genre_id + '">' + g.genre_name + '</a>';
                        });
                    }

                    var starHtml = "";
                    if (movie.movie_stars) {
                        movie.movie_stars.forEach(function(s, i) {
                            if (i > 0) starHtml += ", ";
                            starHtml += '<a href="single-star.html?id=' + s.star_id + '">' + s.star_name + '</a>';
                        });
                    }

                    var rowNum = (currentPage - 1) * currentLimit + index + 1;
                    var rating = movie.movie_rating ? movie.movie_rating.toFixed(1) : "N/A";

                    var row = '<tr>' +
                        '<td>' + rowNum + '</td>' +
                        '<td><a href="single-movie.html?id=' + movie.movie_id + '">' + movie.movie_title + '</a></td>' +
                        '<td>' + movie.movie_year + '</td>' +
                        '<td>' + movie.movie_director + '</td>' +
                        '<td>' + genreHtml + '</td>' +
                        '<td>' + starHtml + '</td>' +
                        '<td>' + rating + '</td>' +
                        '<td><button class="btn btn-sm add-to-cart" data-movie-id="' + movie.movie_id + '">🛒 Add</button></td>' +
                        '</tr>';
                    tbody.append(row);
                });

                // Update pagination buttons
                $("#prev-btn").prop("disabled", currentPage <= 1);
                $("#next-btn").prop("disabled", currentPage >= totalPages);
            },
            error: function() {
                $("#movie-table-body").html('<tr><td colspan="8" style="text-align:center; color:#e94560;">Error loading movies.</td></tr>');
            }
        });
    }

    // Sort change
    $("#sort-select").change(function() {
        currentSort = $(this).val();
        currentPage = 1;
        loadMovies();
    });

    // Limit change
    $("#limit-select").change(function() {
        currentLimit = parseInt($(this).val());
        currentPage = 1;
        loadMovies();
    });

    // Pagination
    $("#prev-btn").click(function() {
        if (currentPage > 1) { currentPage--; loadMovies(); }
    });
    $("#next-btn").click(function() {
        if (currentPage < totalPages) { currentPage++; loadMovies(); }
    });

    // Add to cart
    $(document).on("click", ".add-to-cart", function() {
        var movieId = $(this).data("movie-id");
        var btn = $(this);
        $.ajax({
            url: "../api/cart",
            method: "POST",
            data: { movieId: movieId, action: "add" },
            success: function(data) {
                if (typeof data === "string") data = JSON.parse(data);
                if (data.status === "success") {
                    btn.text("✓ Added").css("background", "#27ae60");
                    setTimeout(function() { btn.text("🛒 Add").css("background", ""); }, 1500);
                    $("#cart-count").text(data.cartSize);
                }
            }
        });
    });

    // Logout
    $("#logout-link").click(function(e) {
        e.preventDefault();
        window.location.href = "login.html";
    });

    // Initial load
    loadMovies();
});
