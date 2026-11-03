FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY index.html /usr/share/nginx/html/index.html
COPY src/styles.css /usr/share/nginx/html/styles.css
COPY src/app.js /usr/share/nginx/html/app.js
COPY src/admin.js /usr/share/nginx/html/admin.js
COPY src/recruiting.js /usr/share/nginx/html/recruiting.js
EXPOSE 8080
