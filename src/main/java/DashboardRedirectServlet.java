import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebServlet(name = "DashboardRedirectServlet", urlPatterns = "/_dashboard")
public class DashboardRedirectServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("employee") != null) {
            response.sendRedirect(request.getContextPath() + "/html/dashboard.html");
        } else {
            response.sendRedirect(request.getContextPath() + "/html/dashboard-login.html");
        }
    }
}
