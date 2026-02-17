$(document).ready(function() {
    var movieId = new URLSearchParams(window.location.search).get("id");

    $.ajax({
        url: "../api/single-movie",
        method: "GET",
        data: { id: movieId },
        success: function(data) {
            if (typeof data === "string") data = JSON.parse(data);

            var genreHtml = "";
            if (data.movie_genres) {
                data.movie_genres.forEach(function(g) {
                    genreHtml += '<span class="tag"><a href="movie-list.html?genreId=' + g.genre_id + '">' + g.genre_name + '</a></span>';
                });
            }

            var starHtml = "";
            if (data.movie_stars) {
                data.movie_stars.forEach(function(s) {
                    starHtml += '<span class="tag"><a href="single-star.html?id=' + s.star_id + '">' + s.star_name + '</a></span>';
                });
            }

            var rating = data.movie_rating ? data.movie_rating.toFixed(1) : "N/A";

            var html = '<h1>' + data.movie_title + ' (' + data.movie_year + ')</h1>' +
                '<div class="info-row"><span class="info-label">Director: </span><span class="info-value">' + data.movie_director + '</span></div>' +
                '<div class="info-row"><span class="info-label">Rating: </span><span class="info-value" style="color:#f1c40f;">⭐ ' + rating + '</span></div>' +
                '<div class="info-row"><span class="info-label">Genres: </span>' + genreHtml + '</div>' +
                '<div class="info-row"><span class="info-label">Stars: </span>' + starHtml + '</div>' +
                '<div style="margin-top: 20px;"><button class="btn add-to-cart" data-movie-id="' + data.movie_id + '">🛒 Add to Cart</button></div>';

            $("#movie-detail").html(html);
        }
    });

    // Add to cart
    $(document).on("click", ".add-to-cart", function() {
        var mid = $(this).data("movie-id");
        var btn = $(this);
        $.ajax({
            url: "../api/cart",
            method: "POST",
            data: { movieId: mid, action: "add" },
            success: function(data) {
                if (typeof data === "string") data = JSON.parse(data);
                if (data.status === "success") {
                    btn.text("✓ Added to Cart").css("background", "#27ae60");
                    $("#cart-count").text(data.cartSize);
                }
            }
        });
    });

    $("#logout-link").click(function(e) { e.preventDefault(); window.location.href = "login.html"; });
});
