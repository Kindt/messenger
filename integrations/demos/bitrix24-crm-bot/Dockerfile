FROM php:8.2-apache
COPY index.php /var/www/html/index.php
RUN a2enmod rewrite
EXPOSE 8080
RUN sed -i 's/Listen 80/Listen 8080/' /etc/apache2/ports.conf \
    && sed -i 's/:80>/:8080>/' /etc/apache2/sites-available/000-default.conf
