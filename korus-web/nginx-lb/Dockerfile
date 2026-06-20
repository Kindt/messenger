FROM nginx:1.27-alpine
COPY nginx.conf.template /etc/nginx/nginx.conf.template
COPY nginx.conf.hotswap.template /etc/nginx/nginx.conf.hotswap.template
COPY nginx.conf.static.template /etc/nginx/nginx.conf.static.template
COPY docker-entrypoint-lb.sh /docker-entrypoint-lb.sh
COPY docker-entrypoint-static.sh /docker-entrypoint-static.sh
RUN chmod +x /docker-entrypoint-lb.sh /docker-entrypoint-static.sh
ENTRYPOINT ["/docker-entrypoint-lb.sh"]
CMD ["nginx", "-g", "daemon off;"]
