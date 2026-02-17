import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;

@WebFilter(filterName = "LoginFilter", urlPatterns = "/*")
public class LoginFilter implements Filter {

    private final ArrayList<String> allowedURIs = new ArrayList<>();

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (isUrlAllowed(httpRequest.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = httpRequest.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            chain.doFilter(request, response);
        } else {
            // For API calls, return JSON error
            if (httpRequest.getRequestURI().contains("/api/")) {
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"status\":\"fail\",\"message\":\"Session expired. Please login.\"}");
            } else {
                httpResponse.sendRedirect(httpRequest.getContextPath() + "/html/login.html");
            }
        }
    }

    private boolean isUrlAllowed(String requestURI) {
        return allowedURIs.stream().anyMatch(requestURI.toLowerCase()::endsWith);
    }

    public void init(FilterConfig filterConfig) {
        allowedURIs.add("login.html");
        allowedURIs.add("login.js");
        allowedURIs.add("login.css");
        allowedURIs.add("/api/login");
        allowedURIs.add("style.css");
    }

    public void destroy() {}
}
