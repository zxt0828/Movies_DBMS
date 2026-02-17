$(document).ready(function() {
    var starId = new URLSearchParams(window.location.search).get("id");

    $.ajax({
        url: "../api/single-star",
        method: "GET",
        data: { id: starId },
        success: function(data) {
            if (typeof data === "string") data = JSON.parse(data);

            var movieHtml = '<table class="movie-table"><thead><tr><th>Title</th><th>Year</th><th>Director</th><th>Rating</th></tr></thead><tbody>';
            if (data.movies) {
                data.movies.forEach(function(m) {
                    var rating = m.movie_rating ? m.movie_rating.toFixed(1) : "N/A";
                    movieHtml += '<tr><td><a href="single-movie.html?id=' + m.movie_id + '">' + m.movie_title + '</a></td>' +
                        '<td>' + m.movie_year + '</td><td>' + m.movie_director + '</td><td>' + rating + '</td></tr>';
                });
            }
            movieHtml += '</tbody></table>';

            var html = '<h1>' + data.star_name + '</h1>' +
                '<div class="info-row"><span class="info-label">Birth Year: </span><span class="info-value">' + data.star_birth_year + '</span></div>' +
                '<div class="info-row" style="margin-top: 20px;"><span class="info-label">Movies:</span></div>' +
                movieHtml;

            $("#star-detail").html(html);
        }
    });

    $("#logout-link").click(function(e) { e.preventDefault(); window.location.href = "login.html"; });
});
