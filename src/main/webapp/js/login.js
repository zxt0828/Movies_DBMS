$(document).ready(function() {
    $("#login-form").submit(function(event) {
        event.preventDefault();
        $.ajax({
            url: "../api/login",
            method: "POST",
            data: {
                email: $("#email").val(),
                password: $("#password").val()
            },
            success: function(response) {
                if (typeof response === "string") response = JSON.parse(response);
                if (response.status === "success") {
                    window.location.href = "main.html";
                } else {
                    $("#error-msg").text(response.message).show();
                }
            },
            error: function() {
                $("#error-msg").text("Unable to connect to server.").show();
            }
        });
    });
});
