$(document).ready(function() {
    // Load genres
    $.ajax({
        url: "../api/genres",
        method: "GET",
        success: function(data) {
            if (typeof data === "string") data = JSON.parse(data);
            var genreHtml = "";
            data.forEach(function(genre) {
                genreHtml += '<a href="movie-list.html?genreId=' + genre.genre_id + '">' + genre.genre_name + '</a>';
            });
            $("#genre-links").html(genreHtml);
        }
    });

    // Generate title links (0-9, A-Z, *)
    var titleHtml = "";
    for (var i = 0; i <= 9; i++) {
        titleHtml += '<a href="movie-list.html?titleInitial=' + i + '">' + i + '</a>';
    }
    for (var c = 65; c <= 90; c++) {
        var letter = String.fromCharCode(c);
        titleHtml += '<a href="movie-list.html?titleInitial=' + letter + '">' + letter + '</a>';
    }
    titleHtml += '<a href="movie-list.html?titleInitial=*">*</a>';
    $("#title-links").html(titleHtml);

    // Search form
    $("#search-form").submit(function(event) {
        event.preventDefault();
        var params = new URLSearchParams();
        var title = $("#title").val().trim();
        var year = $("#year").val().trim();
        var director = $("#director").val().trim();
        var star = $("#star").val().trim();

        if (title) params.set("title", title);
        if (year) params.set("year", year);
        if (director) params.set("director", director);
        if (star) params.set("star", star);

        if (params.toString()) {
            window.location.href = "movie-list.html?" + params.toString();
        }
    });

    // Logout
    $("#logout-link").click(function(e) {
        e.preventDefault();
        // Just redirect to login; session will expire or we could add a logout servlet
        window.location.href = "login.html";
    });
});
