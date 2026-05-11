FROM nginx:1.27-alpine
RUN apk add --no-cache gettext
COPY nginx.conf.template /etc/nginx/nginx.conf.template
COPY docker-entrypoint-lb.sh /docker-entrypoint-lb.sh
RUN chmod +x /docker-entrypoint-lb.sh
ENTRYPOINT ["/docker-entrypoint-lb.sh"]
CMD ["nginx", "-g", "daemon off;"]
