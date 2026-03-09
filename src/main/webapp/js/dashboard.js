$(document).ready(function() {

    // Add Star
    $("#add-star-form").submit(function(e) {
        e.preventDefault();
        $.ajax({
            url: "../api/dashboard/add-star",
            method: "POST",
            data: {
                starName: $("#star-name").val(),
                birthYear: $("#star-birth-year").val()
            },
            success: function(data) {
                if (typeof data === "string") data = JSON.parse(data);
                var color = data.status === "success" ? "#27ae60" : "#e94560";
                $("#add-star-msg").html('<span style="color:' + color + ';">' + data.message + '</span>');
                if (data.status === "success") {
                    $("#star-name").val("");
                    $("#star-birth-year").val("");
                }
            },
            error: function() {
                $("#add-star-msg").html('<span style="color:#e94560;">Error connecting to server.</span>');
            }
        });
    });

    // Add Movie (calls stored procedure)
    $("#add-movie-form").submit(function(e) {
        e.preventDefault();
        $.ajax({
            url: "../api/dashboard/add-movie",
            method: "POST",
            data: {
                title: $("#movie-title").val(),
                year: $("#movie-year").val(),
                director: $("#movie-director").val(),
                starName: $("#movie-star").val(),
                genreName: $("#movie-genre").val()
            },
            success: function(data) {
                if (typeof data === "string") data = JSON.parse(data);
                var color = data.status === "success" ? "#27ae60" : "#e94560";
                $("#add-movie-msg").html('<span style="color:' + color + ';">' + data.message + '</span>');
                if (data.status === "success") {
                    $("#movie-title").val("");
                    $("#movie-year").val("");
                    $("#movie-director").val("");
                    $("#movie-star").val("");
                    $("#movie-genre").val("");
                }
            },
            error: function() {
                $("#add-movie-msg").html('<span style="color:#e94560;">Error connecting to server.</span>');
            }
        });
    });

    // Load Metadata
    $("#load-metadata-btn").click(function() {
        $.ajax({
            url: "../api/dashboard/metadata",
            method: "GET",
            success: function(data) {
                if (typeof data === "string") data = JSON.parse(data);
                if (data.status === "success") {
                    var html = "";
                    data.tables.forEach(function(table) {
                        html += '<div style="margin-bottom:15px;">';
                        html += '<h3 style="color:#e94560;">' + table.table_name + '</h3>';
                        html += '<table class="movie-table"><thead><tr><th>Column</th><th>Type</th><th>Size</th><th>Nullable</th></tr></thead><tbody>';
                        table.columns.forEach(function(col) {
                            html += '<tr><td>' + col.column_name + '</td><td>' + col.column_type + '</td><td>' + col.column_size + '</td><td>' + col.is_nullable + '</td></tr>';
                        });
                        html += '</tbody></table></div>';
                    });
                    $("#metadata-content").html(html);
                } else {
                    $("#metadata-content").html('<span style="color:#e94560;">' + data.message + '</span>');
                }
            }
        });
    });

    // Logout
    $("#logout-link").click(function(e) {
        e.preventDefault();
        window.location.href = "dashboard-login.html";
    });
});
