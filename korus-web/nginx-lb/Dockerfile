FROM nginx:1.27-alpine
COPY nginx.conf.template /etc/nginx/nginx.conf.template
COPY docker-entrypoint-lb.sh /docker-entrypoint-lb.sh
# nginx -t at runtime only (upstream hostnames are not resolvable during image build)
RUN chmod +x /docker-entrypoint-lb.sh
ENTRYPOINT ["/docker-entrypoint-lb.sh"]
CMD ["nginx", "-g", "daemon off;"]
