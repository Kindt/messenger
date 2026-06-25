# Brotli is compiled into nginx (ngx_brotli); stock nginx:alpine has gzip only.
FROM fholzer/nginx-brotli:v1.28.3
COPY nginx.conf.template /etc/nginx/nginx.conf.template
COPY nginx.conf.hotswap.template /etc/nginx/nginx.conf.hotswap.template
COPY nginx.conf.static.template /etc/nginx/nginx.conf.static.template
COPY includes/compression.conf /etc/nginx/includes/compression.conf
COPY docker-entrypoint-lb.sh /docker-entrypoint-lb.sh
COPY docker-entrypoint-static.sh /docker-entrypoint-static.sh
RUN chmod +x /docker-entrypoint-lb.sh /docker-entrypoint-static.sh
ENTRYPOINT ["/docker-entrypoint-lb.sh"]
CMD ["nginx", "-g", "daemon off;"]
