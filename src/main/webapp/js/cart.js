$(document).ready(function() {
    function loadCart() {
        $.ajax({
            url: "../api/cart",
            method: "GET",
            success: function(data) {
                if (typeof data === "string") data = JSON.parse(data);
                var tbody = $("#cart-body");
                tbody.empty();

                if (!data.items || data.items.length === 0) {
                    tbody.append('<tr><td colspan="6" style="text-align:center; color:#aaa;">Your cart is empty.</td></tr>');
                    $("#cart-total").text("");
                    $("#checkout-btn").prop("disabled", true);
                    return;
                }

                var totalCount = 0;
                data.items.forEach(function(item) {
                    totalCount += item.quantity;
                    var row = '<tr>' +
                        '<td>' + item.movie_title + '</td>' +
                        '<td>' + item.movie_year + '</td>' +
                        '<td>$' + item.price.toFixed(2) + '</td>' +
                        '<td><input type="number" class="qty-input" data-movie-id="' + item.movie_id + '" value="' + item.quantity + '" min="0" max="99"></td>' +
                        '<td>$' + item.subtotal.toFixed(2) + '</td>' +
                        '<td><button class="btn btn-sm btn-outline remove-item" data-movie-id="' + item.movie_id + '">Remove</button></td>' +
                        '</tr>';
                    tbody.append(row);
                });

                $("#cart-total").text("Total: $" + data.totalPrice.toFixed(2));
                $("#cart-count").text(totalCount);
            }
        });
    }

    // Update quantity
    $(document).on("change", ".qty-input", function() {
        var movieId = $(this).data("movie-id");
        var quantity = parseInt($(this).val());
        $.ajax({
            url: "../api/cart",
            method: "POST",
            data: { movieId: movieId, action: "update", quantity: quantity },
            success: function() { loadCart(); }
        });
    });

    // Remove item
    $(document).on("click", ".remove-item", function() {
        var movieId = $(this).data("movie-id");
        $.ajax({
            url: "../api/cart",
            method: "POST",
            data: { movieId: movieId, action: "remove" },
            success: function() { loadCart(); }
        });
    });

    // Show checkout form
    $("#checkout-btn").click(function() {
        $("#checkout-section").show();
        $("html, body").animate({ scrollTop: $("#checkout-section").offset().top }, 500);
    });

    // Submit checkout
    $("#checkout-form").submit(function(e) {
        e.preventDefault();
        $.ajax({
            url: "../api/checkout",
            method: "POST",
            data: {
                firstName: $("#cc-firstName").val(),
                lastName: $("#cc-lastName").val(),
                creditCard: $("#cc-number").val(),
                expiration: $("#cc-expiration").val()
            },
            success: function(data) {
                if (typeof data === "string") data = JSON.parse(data);
                if (data.status === "success") {
                    // Show confirmation
                    $(".section-card").hide();
                    $("#checkout-section").hide();
                    var confBody = $("#confirmation-body");
                    confBody.empty();
                    var total = 0;
                    data.sales.forEach(function(s) {
                        total += s.subtotal;
                        confBody.append('<tr><td>' + s.movie_title + '</td><td>' + s.quantity + '</td><td>$' + s.subtotal.toFixed(2) + '</td></tr>');
                    });
                    confBody.append('<tr style="font-weight:bold; color:#e94560;"><td colspan="2">Total</td><td>$' + total.toFixed(2) + '</td></tr>');
                    $("#confirmation-section").show();
                    $("#cart-count").text("0");
                } else {
                    $("#checkout-error").text(data.message).show();
                }
            }
        });
    });

    $("#logout-link").click(function(e) { e.preventDefault(); window.location.href = "login.html"; });

    loadCart();
});
