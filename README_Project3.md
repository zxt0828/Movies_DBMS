# CS122B Project 3 - Deployment Instructions

## Overview of Changes

### Task 2: reCAPTCHA
- Updated `login.html` - added Google reCAPTCHA widget
- Updated `login.js` - sends `g-recaptcha-response` with login request
- Updated `LoginServlet.java` - verifies reCAPTCHA on server side

### Task 3: HTTPS
- Updated `web.xml` - added `<security-constraint>` for CONFIDENTIAL transport
- Need to configure Tomcat keystore (see below)

### Task 4: Prepared Statements
- Already implemented in Project 2 (all servlets use PreparedStatement)

### Task 5: Encrypted Passwords
- Updated `LoginServlet.java` - uses jasypt `StrongPasswordEncryptor.checkPassword()`
- Added `UpdateSecurePassword.java` - one-time utility to encrypt existing passwords
- Added jasypt dependency in `pom.xml`
- Altered customers/employees password column to VARCHAR(128)

### Task 6: Dashboard
- New files: `Employee.java`, `EmployeeLoginServlet.java`, `DashboardServlet.java`, `DashboardRedirectServlet.java`
- New HTML/JS: `dashboard-login.html`, `dashboard.html`, `dashboard-login.js`, `dashboard.js`
- Created `add_movie` stored procedure in `create_tables.sql`
- Updated `LoginFilter.java` to allow dashboard routes

### Task 7: XML Parsing
- `XmlParser.java` - DOM-based parser for Stanford movie XML files
- `xml_parser_optimization_report.md` - describes optimization techniques

---

## Step-by-Step Deployment on AWS

### 1. SSH into AWS
```bash
ssh -i your-key.pem ubuntu@3.149.8.8
```

### 2. Run SQL Setup Script
```bash
mysql -u mytestuser -p moviedb < create_tables.sql
# Enter password: My6$Password
```

### 3. Configure HTTPS Keystore (Task 3)
```bash
sudo keytool -genkey -alias fabflix -keyalg RSA -keystore /home/ubuntu/apache-tomcat-9.0.113/keystore
# Use password: changeit
```

Edit `/home/ubuntu/apache-tomcat-9.0.113/conf/server.xml`, uncomment and modify the SSL connector:
```xml
<Connector port="8443"
    protocol="HTTP/1.1"
    connectionTimeout="20000"
    redirectPort="8443"
    SSLEnabled="true"
    scheme="https"
    secure="true"
    sslProtocol="TLS"
    keystoreFile="/home/ubuntu/apache-tomcat-9.0.113/keystore"
    keystorePass="changeit" />
```

Make sure port 8443 is open in AWS Security Group.

### 4. Build and Deploy
```bash
cd Movies_DBMS
mvn clean package
cp target/fabflix.war /home/ubuntu/apache-tomcat-9.0.113/webapps/
```

### 5. Encrypt Passwords (Task 5) - Run ONCE only!
```bash
cd target/fabflix/WEB-INF
java -cp "lib/*:classes" UpdateSecurePassword
```

### 6. Restart Tomcat
```bash
cd /home/ubuntu/apache-tomcat-9.0.113
./bin/shutdown.sh
./bin/startup.sh
```

### 7. XML Parsing (Task 7)
Download and extract the Stanford movies XML files, then run:
```bash
cd target/fabflix/WEB-INF
java -cp "lib/*:classes" XmlParser /path/to/stanford-movies/
```

### 8. Verify
- Customer Login: `https://3.149.8.8:8443/fabflix/html/login.html`
- Employee Dashboard: `https://3.149.8.8:8443/fabflix/_dashboard`
- Employee credentials: `classta@email.edu` / `classta`
