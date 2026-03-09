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
        String requestURI = httpRequest.getRequestURI();

        if (isUrlAllowed(requestURI)) {
            chain.doFilter(request, response);
            return;
        }

        // Check if this is a dashboard request
        if (requestURI.contains("_dashboard") || requestURI.contains("/api/dashboard") || requestURI.contains("/api/employee-login")) {
            HttpSession session = httpRequest.getSession(false);
            if (session != null && session.getAttribute("employee") != null) {
                chain.doFilter(request, response);
            } else {
                if (requestURI.contains("/api/")) {
                    httpResponse.setContentType("application/json");
                    httpResponse.getWriter().write("{\"status\":\"fail\",\"message\":\"Employee session expired. Please login.\"}");
                } else {
                    httpResponse.sendRedirect(httpRequest.getContextPath() + "/_dashboard");
                }
            }
            return;
        }

        HttpSession session = httpRequest.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            chain.doFilter(request, response);
        } else {
            if (requestURI.contains("/api/")) {
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
        // Dashboard login pages
        allowedURIs.add("/_dashboard");
        allowedURIs.add("/api/employee-login");
        allowedURIs.add("dashboard-login.html");
        allowedURIs.add("dashboard-login.js");
    }

    public void destroy() {}
}
